#!/usr/bin/env python3
"""Fine-tune the last DINOv2 blocks plus a projection head for same-cat matching (needs a GPU or Apple MPS).

The larger sibling of train_head.py: instead of training only a head on frozen vectors, the last
``--unfreeze`` transformer blocks of DINOv2-base are trained too, on augmented cat crops from crop.py:

    embedding-service/.venv/bin/python eval/reid/finetune.py \\
        eval/reid/data/crops/train/crops.csv eval/reid/data/crops/eval/crops.csv

Same honesty rules as train_head.py: training cats are split by cat into train / validation, early stopping
and the match threshold use validation only, and the evaluation set is scored once at the end against plain
DINOv2 on the same crops. Only the trained weights (last blocks, final norm, head) are saved.
"""

from __future__ import annotations

import argparse
import sys
import time
from pathlib import Path

import numpy as np
import torch
from PIL import Image
from torch import nn
from torchvision import transforms
from transformers import AutoModel

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
sys.path.insert(0, str(Path(__file__).resolve().parent))
from baseline import reid_metrics  # noqa: E402
from run_eval import read_csv  # noqa: E402
from train_head import Head, at_threshold, pk_batches, supcon_loss, threshold_for_recall  # noqa: E402

MODEL_ID = "facebook/dinov2-base"
DEFAULT_OUT = Path(__file__).resolve().parent / "data" / "finetuned.pt"
MEAN, STD = (0.485, 0.456, 0.406), (0.229, 0.224, 0.225)  # DINOv2 processor settings

# Evaluation matches the DINOv2 processor (resize 256, center crop 224). Training adds mild augmentation only:
# no horizontal flip (coat patterns are not symmetric) and little colour change (coat colour identifies cats).
EVAL_TF = transforms.Compose([
    transforms.Resize(256, interpolation=transforms.InterpolationMode.BICUBIC),
    transforms.CenterCrop(224), transforms.ToTensor(), transforms.Normalize(MEAN, STD),
])
TRAIN_TF = transforms.Compose([
    transforms.RandomResizedCrop(224, scale=(0.6, 1.0), ratio=(0.85, 1.15),
                                 interpolation=transforms.InterpolationMode.BICUBIC),
    transforms.RandomRotation(10),
    transforms.ColorJitter(brightness=0.25, contrast=0.25, saturation=0.1),
    transforms.RandomApply([transforms.GaussianBlur(5)], p=0.2),
    transforms.ToTensor(), transforms.Normalize(MEAN, STD),
])


def device() -> str:
    return "cuda" if torch.cuda.is_available() else ("mps" if torch.backends.mps.is_available() else "cpu")


class CatEncoder(nn.Module):
    def __init__(self, unfreeze: int, hidden: int, dim: int):
        super().__init__()
        self.backbone = AutoModel.from_pretrained(MODEL_ID)
        for p in self.backbone.parameters():
            p.requires_grad = False
        self.trainable = nn.ModuleList([*self.backbone.encoder.layer[-unfreeze:], self.backbone.layernorm]) if unfreeze else nn.ModuleList()
        for p in self.trainable.parameters():
            p.requires_grad = True
        self.head = Head(self.backbone.config.hidden_size, hidden, dim)

    def cls(self, pixels: torch.Tensor) -> torch.Tensor:
        return nn.functional.normalize(self.backbone(pixel_values=pixels).pooler_output, dim=1)

    def forward(self, pixels: torch.Tensor) -> torch.Tensor:
        return self.head(self.cls(pixels))

    def trained_state(self) -> dict:
        names = {n for n, p in self.named_parameters() if p.requires_grad}
        return {n: t.detach().cpu().clone() for n, t in self.state_dict().items() if n in names}


class Crops(torch.utils.data.Dataset):
    """Augmented training crops (module-level so DataLoader workers can pickle it)."""

    def __init__(self, paths: list[Path], labels: torch.Tensor):
        self.paths, self.labels = paths, labels

    def __len__(self) -> int:
        return len(self.paths)

    def __getitem__(self, i: int):
        return TRAIN_TF(Image.open(self.paths[i]).convert("RGB")), self.labels[i]


def load_crops(csv_path: Path) -> tuple[list[Path], np.ndarray]:
    rows = read_csv(csv_path)
    return [(csv_path.parent / r["image"]).resolve() for r in rows], np.array([r["cat_id"] for r in rows])


@torch.no_grad()
def embed(fn, paths: list[Path], dev: str, batch: int = 64) -> np.ndarray:
    out = []
    for i in range(0, len(paths), batch):
        x = torch.stack([EVAL_TF(Image.open(p).convert("RGB")) for p in paths[i:i + batch]]).to(dev)
        out.append(fn(x).float().cpu())
    return torch.cat(out).numpy()


def main() -> None:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("train", type=Path, help="crops.csv from crop.py (training cats)")
    ap.add_argument("eval", type=Path, help="crops.csv from crop.py (eval/cats.csv)")
    ap.add_argument("--out", type=Path, default=DEFAULT_OUT)
    ap.add_argument("--unfreeze", type=int, default=2, help="last DINOv2 blocks to train (0 = head only)")
    ap.add_argument("--dim", type=int, default=256)
    ap.add_argument("--hidden", type=int, default=512)
    ap.add_argument("--epochs", type=int, default=15)
    ap.add_argument("--p", type=int, default=32, help="cats per batch")
    ap.add_argument("--k", type=int, default=4, help="photos per cat per batch")
    ap.add_argument("--lr", type=float, default=1e-3, help="head learning rate (DINOv2 blocks use lr / 20)")
    ap.add_argument("--temperature", type=float, default=0.1)
    ap.add_argument("--val-frac", type=float, default=0.1)
    ap.add_argument("--target-recall", type=float, default=0.8)
    ap.add_argument("--patience", type=int, default=4)
    ap.add_argument("--workers", type=int, default=4)
    args = ap.parse_args()
    torch.manual_seed(0)
    rng = np.random.default_rng(0)
    dev = device()

    paths, ids = load_crops(args.train)
    eval_paths, eval_ids = load_crops(args.eval)
    cats, counts = np.unique(ids, return_counts=True)
    multi = set(cats[counts >= 2])
    keep = np.array([c in multi for c in ids])
    paths, ids = [p for p, k in zip(paths, keep) if k], ids[keep]
    val_cats = set(rng.permutation(sorted(multi))[: max(2, int(len(multi) * args.val_frac))])
    is_val = np.array([c in val_cats for c in ids])
    tr_paths, tr_ids = [p for p, v in zip(paths, is_val) if not v], ids[~is_val]
    val_paths, val_ids = [p for p, v in zip(paths, is_val) if v], ids[is_val]
    p = min(args.p, len(set(tr_ids)))
    if len(set(tr_ids)) < 4:
        raise SystemExit("need at least 4 training cats with 2+ photos")
    print(f"device {dev}; train {len(set(tr_ids))} cats / {len(tr_ids)} photos, validation {len(val_cats)} cats / "
          f"{len(val_ids)} photos, eval {len(set(eval_ids))} cats / {len(eval_ids)} photos; unfreeze {args.unfreeze} blocks")

    model = CatEncoder(args.unfreeze, args.hidden, args.dim).to(dev)
    model.eval()
    plain = {"val": embed(model.cls, val_paths, dev), "eval": embed(model.cls, eval_paths, dev)}
    print(f"plain DINOv2 validation mAP {reid_metrics(plain['val'], val_ids)['mAP']:.3f}")

    opt = torch.optim.AdamW([
        {"params": model.head.parameters(), "lr": args.lr},
        {"params": model.trainable.parameters(), "lr": args.lr / 20},
    ], weight_decay=1e-4)
    sched = torch.optim.lr_scheduler.CosineAnnealingLR(opt, T_max=args.epochs)
    crops = Crops(tr_paths, torch.from_numpy(np.unique(tr_ids, return_inverse=True)[1]))

    best_map, best_state, best_epoch, stale = -1.0, None, 0, 0
    for epoch in range(1, args.epochs + 1):
        model.train()
        batches = list(pk_batches(tr_ids, p, args.k, rng))
        loader = torch.utils.data.DataLoader(crops, batch_sampler=batches, num_workers=args.workers)
        losses, start = [], time.time()
        for x, y in loader:
            loss = supcon_loss(model(x.to(dev)), y.to(dev), args.temperature)
            opt.zero_grad()
            loss.backward()
            opt.step()
            losses.append(loss.item())
        sched.step()
        model.eval()
        val_map = reid_metrics(embed(model, val_paths, dev), val_ids)["mAP"]
        print(f"  epoch {epoch:>2}  loss {np.mean(losses):.3f}  validation mAP {val_map:.3f}  ({time.time() - start:.0f}s)", flush=True)
        if val_map > best_map:
            best_map, best_epoch, stale, best_state = val_map, epoch, 0, model.trained_state()
        else:
            stale += 1
            if stale >= args.patience:
                break
    model.load_state_dict(best_state, strict=False)
    model.eval()
    tuned = {"val": embed(model, val_paths, dev), "eval": embed(model, eval_paths, dev)}
    print(f"best epoch {best_epoch} (validation mAP {best_map:.3f})\n")

    print(f"Evaluation set ({args.eval}); threshold set on validation for {args.target_recall:.0%} recall")
    print(f"  {'model':<16} {'rank-1':>7} {'mAP':>6} {'AUC':>6}   {'threshold':>9} {'recall':>7} {'false merges':>13}")
    threshold = None
    for name, e in (("dinov2", plain), ("dinov2-finetuned", tuned)):
        t = threshold_for_recall(e["val"], val_ids, args.target_recall)
        m = reid_metrics(e["eval"], eval_ids)
        rec, fm, neg = at_threshold(e["eval"], eval_ids, t)
        print(f"  {name:<16} {m['rank1']:>7.0%} {m['mAP']:>6.2f} {m['pair_auc']:>6.2f}   {t:>9.3f} {rec:>7.0%} {fm:>6}/{neg:<6}")
        threshold = t

    args.out.parent.mkdir(parents=True, exist_ok=True)
    torch.save({"trained_state": best_state, "model_id": MODEL_ID, "unfreeze": args.unfreeze, "hidden": args.hidden,
                "dim": args.dim, "threshold": threshold, "best_epoch": best_epoch,
                "input": "cat crop, square pad, resize 256 / center crop 224, ImageNet normalisation"}, args.out)
    print(f"\nsaved {args.out} (use only if dinov2-finetuned beats dinov2 above)")


if __name__ == "__main__":
    main()
