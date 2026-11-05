#!/usr/bin/env python3
"""No-training baselines for same-cat matching and ear-tip detection.

Answers two questions before any model is trained:

  same-cat  Which off-the-shelf image backbone tells individual cats apart best on your photos? CLIP ViT-B/32
            (the app's previous encoder) vs. DINOv2 (the app's default) vs. two models trained for animal
            re-identification: MegaDescriptor (wildlife) and AvitoTech's SigLIP (pets, ~1.9M photos).
  ear-tip   How far does a linear probe (logistic regression on frozen backbone features of the head crop) get?

Runs locally in the embedding-service virtualenv; no backend, OpenAI key or running service is needed. Model
weights download from Hugging Face on first use.

    embedding-service/.venv/bin/pip install -r eval/requirements-baseline.txt
    embedding-service/.venv/bin/python eval/baseline.py same-cat eval/cats.csv     [--backbones clip,dinov2] [--crop none]
    embedding-service/.venv/bin/python eval/baseline.py ear-tip  eval/ear_tip_labels.csv  [--max-false-yes 0.05]

Image paths in the CSVs are relative to the CSV file. Results are written next to the CSV (``*_results.csv``).
"""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

import numpy as np
import torch
from PIL import Image

from run_eval import read_csv, write_results

sys.path.insert(0, str(Path(__file__).resolve().parent.parent / "embedding-service"))
import detector  # noqa: E402  (OWL-ViT cat / head detector shared with the embedding service)

BACKBONES = {
    "clip": "openai/clip-vit-base-patch32",
    "dinov2": "facebook/dinov2-base",
    "mega": "hf-hub:BVRA/MegaDescriptor-T-224",
    "siglip": "AvitoTech/SigLIP-Base-for-animal-identification",
}
BATCH = 16


def device() -> str:
    return "mps" if torch.backends.mps.is_available() else ("cuda" if torch.cuda.is_available() else "cpu")


# --- images ----------------------------------------------------------------------------------------------------

def square(img: Image.Image) -> Image.Image:
    """Pad to a square so the backbones' center crop cannot cut off ear tips or tails."""
    side = max(img.size)
    out = Image.new("RGB", (side, side), (124, 116, 104))
    out.paste(img, ((side - img.width) // 2, (side - img.height) // 2))
    return out


def prepare(paths: list[Path], crop: str) -> tuple[list[Image.Image], int]:
    """Crop every photo once ("cat", "head" or "none"); returns the crops and how many fell back to a wider view."""
    if crop != "none" and not detector.is_loaded():
        detector.load()
    crops, fallbacks = [], 0
    for p in paths:
        img = Image.open(p).convert("RGB")
        if crop == "none":
            crops.append(square(img))
            continue
        det = detector.detect(img)
        if crop == "head" and det.head is not None:
            crops.append(square(detector.crop_head(img, det.head)))
        elif det.cat is not None:
            fallbacks += crop == "head"
            crops.append(square(detector.crop_cat(img, det.cat)))
        else:
            fallbacks += 1
            crops.append(square(img))
    return crops, fallbacks


# --- backbones -------------------------------------------------------------------------------------------------

class Backbone:
    """Frozen image encoder returning L2-normalised feature vectors."""

    def __init__(self, name: str):
        self.name = name
        self.device = device()
        model_id = BACKBONES[name]
        if name == "mega":
            import timm

            self.model = timm.create_model(model_id, pretrained=True, num_classes=0)
            cfg = timm.data.resolve_data_config({}, model=self.model)
            self.transform = timm.data.create_transform(**cfg)
        else:
            from transformers import AutoImageProcessor, AutoModel, CLIPModel

            self.model = CLIPModel.from_pretrained(model_id) if name == "clip" else AutoModel.from_pretrained(model_id)
            self.processor = AutoImageProcessor.from_pretrained(model_id)
        self.model.eval().to(self.device)

    @torch.no_grad()
    def _forward(self, images: list[Image.Image]) -> torch.Tensor:
        if self.name == "mega":
            return self.model(torch.stack([self.transform(i) for i in images]).to(self.device))
        pixels = self.processor(images=images, return_tensors="pt")["pixel_values"].to(self.device)
        if self.name in ("clip", "siglip"):
            out = self.model.get_image_features(pixel_values=pixels)
            return out if isinstance(out, torch.Tensor) else out.pooler_output
        return self.model(pixel_values=pixels).pooler_output  # DINOv2 CLS token

    def embed(self, images: list[Image.Image]) -> np.ndarray:
        chunks = [self._forward(images[i:i + BATCH]).float().cpu() for i in range(0, len(images), BATCH)]
        feats = torch.cat(chunks)
        return torch.nn.functional.normalize(feats, dim=1).numpy()


def parse_backbones(value: str) -> list[str]:
    names = [n.strip() for n in value.split(",") if n.strip()]
    unknown = [n for n in names if n not in BACKBONES]
    if unknown:
        raise SystemExit(f"unknown backbone(s) {unknown}; choose from {list(BACKBONES)}")
    return names


# --- same cat --------------------------------------------------------------------------------------------------

def reid_metrics(emb: np.ndarray, ids: np.ndarray) -> dict:
    """Retrieval (rank-1, mAP over queries that have another photo of the same cat) and pair-threshold metrics."""
    from sklearn.metrics import roc_auc_score

    sim = emb @ emb.T
    n = len(ids)
    rank1, aps = [], []
    for i in range(n):
        others = np.arange(n) != i
        positives = (ids == ids[i])[others]
        if not positives.any():
            continue
        relevant = positives[np.argsort(-sim[i][others])]
        hits = np.flatnonzero(relevant)
        rank1.append(relevant[0])
        aps.append(np.mean(np.arange(1, len(hits) + 1) / (hits + 1)))

    iu = np.triu_indices(n, 1)
    dist = 1.0 - sim[iu]
    same = (ids[:, None] == ids[None, :])[iu]
    best = (0.0, 0.0, 0.0, -1.0, 0.0)  # threshold, precision, recall, F1, false-merge rate
    for t in np.unique(np.round(dist, 4)):
        match = dist <= t
        tp, fp = int((match & same).sum()), int((match & ~same).sum())
        prec, rec = (tp / (tp + fp) if tp + fp else 0.0), tp / same.sum()
        f1 = 2 * prec * rec / (prec + rec) if prec + rec else 0.0
        if f1 > best[3]:
            best = (float(t), prec, rec, f1, fp / max(1, (~same).sum()))
    return {
        "queries": len(rank1),
        "rank1": float(np.mean(rank1)),
        "mAP": float(np.mean(aps)),
        "pair_auc": float(roc_auc_score(same, -dist)),
        "best_threshold": round(best[0], 4),
        "precision": best[1],
        "recall": best[2],
        "false_merge_rate": best[4],
    }


def run_same_cat(args: argparse.Namespace) -> None:
    csv_path = Path(args.csv)
    rows = read_csv(csv_path)
    ids = np.array([r["cat_id"].strip() for r in rows])
    cats, counts = np.unique(ids, return_counts=True)
    if len(cats) < 2 or counts.max() < 2:
        raise SystemExit("need at least 2 cats and at least one cat with 2+ photos (columns: image,cat_id)")

    crops, fallbacks = prepare([(csv_path.parent / r["image"].strip()).resolve() for r in rows], args.crop)
    print(f"Same-cat baseline — {len(rows)} photos of {len(cats)} cats, crop={args.crop}"
          f" ({fallbacks} without a detected cat), device={device()}\n")
    print(f"  {'backbone':<8} {'rank-1':>7} {'mAP':>6} {'AUC':>6}   {'best thr':>8} {'prec':>6} {'recall':>6} {'false merge':>11}")
    results = []
    for name in parse_backbones(args.backbones):
        m = reid_metrics(Backbone(name).embed(crops), ids)
        results.append({"backbone": name, "crop": args.crop, **{k: round(v, 4) for k, v in m.items()}})
        print(f"  {name:<8} {m['rank1']:>7.0%} {m['mAP']:>6.2f} {m['pair_auc']:>6.2f}   {m['best_threshold']:>8.3f}"
              f" {m['precision']:>6.0%} {m['recall']:>6.0%} {m['false_merge_rate']:>11.1%}")
    print("\n  rank-1: share of photos whose nearest other photo is the same cat. mAP / AUC: overall separation.")
    print("  Thresholds are cosine distances and differ per backbone; the app's 0.35 applies to clip only.")
    print("  Image-only: the app adds location / time gating and volunteer confirmation on top.")
    print(f"  summary: {write_results(csv_path, results, 'baseline')}")


# --- ear tip ---------------------------------------------------------------------------------------------------

def run_ear_tip(args: argparse.Namespace) -> None:
    from sklearn.linear_model import LogisticRegression
    from sklearn.metrics import roc_auc_score
    from sklearn.model_selection import StratifiedGroupKFold, cross_val_predict
    from sklearn.pipeline import make_pipeline
    from sklearn.preprocessing import StandardScaler

    csv_path = Path(args.csv)
    all_rows = read_csv(csv_path)
    rows = [r for r in all_rows if r["label"].strip().lower() in ("yes", "no")]
    y = np.array([r["label"].strip().lower() == "yes" for r in rows], dtype=int)
    # Photos of the same cat stay in the same fold; without a cat_id column every photo is its own group.
    groups = np.array([(r.get("cat_id") or "").strip() or r["image"].strip() for r in rows])
    n_yes, n_no = int(y.sum()), int(len(y) - y.sum())
    folds = min(5, n_yes, n_no, len(np.unique(groups[y == 1])), len(np.unique(groups[y == 0])))
    if folds < 2:
        raise SystemExit(f"need at least 2 'yes' and 2 'no' photos (from different cats); have {n_yes} yes / {n_no} no")

    crops, fallbacks = prepare([(csv_path.parent / r["image"].strip()).resolve() for r in rows], args.crop)
    print(f"Ear-tip linear probe — {n_yes} tipped / {n_no} intact ({len(all_rows) - len(rows)} 'unclear' skipped),"
          f" crop={args.crop} ({fallbacks} without a head), {folds}-fold CV, device={device()}\n")
    print(f"  {'backbone':<8} {'AUC':>6}   {'@0.5: false yes':>15} {'missed':>7}   "
          f"{'yes-threshold':>13} {'tips caught':>11}")
    per_photo = [{"image": r["image"], "label": r["label"]} for r in rows]
    results = []
    for name in parse_backbones(args.backbones):
        x = Backbone(name).embed(crops)
        probe = make_pipeline(StandardScaler(), LogisticRegression(class_weight="balanced", max_iter=5000))
        cv = StratifiedGroupKFold(n_splits=folds, shuffle=True, random_state=0)
        try:
            p = cross_val_predict(probe, x, y, cv=cv, groups=groups, method="predict_proba")[:, 1]
        except ValueError as e:
            print(f"  {name:<8} skipped: {e}")
            continue
        false_yes = float(((p >= 0.5) & (y == 0)).sum() / n_no)
        missed = float(((p < 0.5) & (y == 1)).sum() / n_yes)
        # Lowest threshold whose false-"tipped" rate stays within the budget; below it the app would say "unclear".
        t_yes = next((t for t in np.unique(p) if ((p >= t) & (y == 0)).sum() / n_no <= args.max_false_yes), 1.0)
        caught = float(((p >= t_yes) & (y == 1)).sum() / n_yes)
        auc = float(roc_auc_score(y, p))
        results.append({"backbone": name, "crop": args.crop, "auc": round(auc, 4), "false_yes_at_0.5": round(false_yes, 4),
                        "missed_at_0.5": round(missed, 4), "yes_threshold": round(float(t_yes), 4),
                        "tips_caught": round(caught, 4)})
        for row, prob in zip(per_photo, p):
            row[f"p_tipped_{name}"] = round(float(prob), 4)
        print(f"  {name:<8} {auc:>6.2f}   {false_yes:>15.0%} {missed:>7.0%}   {t_yes:>13.3f} {caught:>11.0%}")
    print(f"\n  false yes: intact cats called tipped (the costly error). yes-threshold: lowest probability that keeps")
    print(f"  false yes <= {args.max_false_yes:.0%}; 'tips caught' is the share of tipped cats still called yes there.")
    print("  Thresholds are picked on the same out-of-fold predictions, so they are optimistic on small sets.")
    print(f"  summary: {write_results(csv_path, results, 'probe')}")
    print(f"  per-photo: {write_results(csv_path, per_photo, 'probe_photos')}")


def main() -> None:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = ap.add_subparsers(dest="cmd", required=True)
    s = sub.add_parser("same-cat", help="compare backbones for telling individual cats apart (CSV: image,cat_id)")
    s.add_argument("csv")
    s.add_argument("--backbones", default=",".join(BACKBONES))
    s.add_argument("--crop", choices=["cat", "none"], default="cat")
    e = sub.add_parser("ear-tip", help="linear probe on head crops (CSV: image,label[,cat_id])")
    e.add_argument("csv")
    e.add_argument("--backbones", default=",".join(BACKBONES))
    e.add_argument("--crop", choices=["head", "cat", "none"], default="head")
    e.add_argument("--max-false-yes", type=float, default=0.05, help="false-'tipped' budget for the yes threshold")
    args = ap.parse_args()
    run_same_cat(args) if args.cmd == "same-cat" else run_ear_tip(args)


if __name__ == "__main__":
    main()
