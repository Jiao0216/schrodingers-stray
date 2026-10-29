#!/usr/bin/env python3
"""Accuracy evaluation for ear-tip (TNR clip) detection and same-cat matching.

Standard library only. Usage (from the repo root, with the backend on :8090 and embedding-service on :8001):

    python3 eval/run_eval.py ear-tip  eval/ear_tip_labels.csv  [--votes 3] [--model gpt-4o] [--no-head-crop] [--mode main]
    python3 eval/run_eval.py same-cat eval/same_cat_pairs.csv  [--no-crop]

Image paths in the CSVs are relative to the CSV file. Per-photo results are written next to the CSV
(``*_results.csv``) so runs with different settings can be compared. The ear-tip run calls OpenAI
(votes × photos requests), so it costs money; the same-cat run is local only.
"""

from __future__ import annotations

import argparse
import csv
import json
import math
import mimetypes
import sys
import urllib.error
import urllib.request
import uuid
from pathlib import Path

BACKEND = "http://127.0.0.1:8090"
EMBEDDING = "http://127.0.0.1:8001"


def post_multipart(url: str, field: str, path: Path, params: dict[str, str] | None = None) -> dict:
    """POST one file as multipart/form-data (plus optional text fields) and return the JSON response."""
    boundary = uuid.uuid4().hex
    mime = mimetypes.guess_type(path.name)[0] or "image/jpeg"
    parts: list[bytes] = []
    for k, v in (params or {}).items():
        parts.append(f'--{boundary}\r\nContent-Disposition: form-data; name="{k}"\r\n\r\n{v}\r\n'.encode())
    parts.append(
        f'--{boundary}\r\nContent-Disposition: form-data; name="{field}"; filename="{path.name}"\r\n'
        f"Content-Type: {mime}\r\n\r\n".encode()
    )
    parts.append(path.read_bytes())
    parts.append(f"\r\n--{boundary}--\r\n".encode())
    req = urllib.request.Request(
        url, data=b"".join(parts), method="POST",
        headers={"Content-Type": f"multipart/form-data; boundary={boundary}"},
    )
    with urllib.request.urlopen(req, timeout=180) as r:
        return json.loads(r.read().decode())


def read_csv(path: Path) -> list[dict[str, str]]:
    with path.open(newline="", encoding="utf-8") as f:
        lines = [ln for ln in f if not ln.lstrip().startswith("#")]  # "#" lines are comments
    return [row for row in csv.DictReader(lines) if any((v or "").strip() for v in row.values())]


def write_results(csv_path: Path, rows: list[dict], suffix: str) -> Path:
    out = csv_path.with_name(csv_path.stem + f"_{suffix}_results.csv")
    if rows:
        with out.open("w", newline="", encoding="utf-8") as f:
            w = csv.DictWriter(f, fieldnames=list(rows[0].keys()))
            w.writeheader()
            w.writerows(rows)
    return out


# --- ear tip ---------------------------------------------------------------------------------------------------

def run_ear_tip(args: argparse.Namespace) -> None:
    csv_path = Path(args.csv)
    labels = read_csv(csv_path)
    params = {"mode": args.mode, "headCrop": "false" if args.no_head_crop else "true"}
    if args.votes:
        params["votes"] = str(args.votes)
    if args.model:
        params["model"] = args.model

    results = []
    for row in labels:
        truth = row["label"].strip().lower()
        img = (csv_path.parent / row["image"].strip()).resolve()
        try:
            r = post_multipart(f"{BACKEND}/api/v1/admin/eval/ear-tip", "image", img, params)
            pred = r.get("verdict", "")
            results.append({"image": row["image"], "label": truth, "predicted": pred,
                            "confidence": r.get("confidencePercent", ""),
                            "votes": f'{r.get("votesYes", "")}/{r.get("votesNo", "")}/{r.get("votesUnclear", "")}',
                            "head_crop": r.get("headCropUsed", ""), "basis_en": r.get("basisEn", "")})
            print(f"  {row['image']:<40} truth={truth:<8} pred={pred:<8} conf={r.get('confidencePercent')}")
        except (urllib.error.URLError, OSError, ValueError) as e:
            print(f"  {row['image']:<40} ERROR {e}", file=sys.stderr)

    decided_truth = [x for x in results if x["label"] in ("yes", "no")]
    tp = sum(1 for x in decided_truth if x["label"] == "yes" and x["predicted"] == "yes")
    fp = sum(1 for x in decided_truth if x["label"] == "no" and x["predicted"] == "yes")
    fn = sum(1 for x in decided_truth if x["label"] == "yes" and x["predicted"] == "no")
    tn = sum(1 for x in decided_truth if x["label"] == "no" and x["predicted"] == "no")
    unclear = sum(1 for x in decided_truth if x["predicted"] == "unclear")
    truth_no = sum(1 for x in decided_truth if x["label"] == "no")
    truth_yes = sum(1 for x in decided_truth if x["label"] == "yes")
    decided = tp + fp + fn + tn

    def pct(a: int, b: int) -> str:
        return f"{100 * a / b:.0f}%" if b else "n/a"

    print(f"\nEar-tip — {len(results)} photos, settings {params}")
    print(f"  confusion (truth→pred): yes→yes {tp}  yes→no {fn}  no→yes {fp}  no→no {tn}  →unclear {unclear}")
    print(f"  FALSE 'tipped' rate (intact cats called tipped — the costly error): {pct(fp, truth_no)}")
    print(f"  missed tips (tipped cats called intact):                         {pct(fn, truth_yes)}")
    print(f"  accuracy when the model decides:                                 {pct(tp + tn, decided)}")
    print(f"  'unclear' rate (sent to a human):                                {pct(unclear, len(decided_truth))}")
    print(f"  per-photo results: {write_results(csv_path, results, 'ear_tip')}")


# --- same cat --------------------------------------------------------------------------------------------------

def cosine_distance(a: list[float], b: list[float]) -> float:
    dot = sum(x * y for x, y in zip(a, b))
    na = math.sqrt(sum(x * x for x in a))
    nb = math.sqrt(sum(y * y for y in b))
    return 1.0 - dot / (na * nb) if na and nb else 1.0


def run_same_cat(args: argparse.Namespace) -> None:
    csv_path = Path(args.csv)
    pairs = read_csv(csv_path)
    crop = "false" if args.no_crop else "true"
    cache: dict[str, list[float]] = {}

    def embed(rel: str) -> list[float]:
        if rel not in cache:
            img = (csv_path.parent / rel.strip()).resolve()
            cache[rel] = post_multipart(f"{EMBEDDING}/embed?crop={crop}", "file", img)["embedding"]
        return cache[rel]

    results = []
    for p in pairs:
        same = p["same"].strip() in ("1", "yes", "true")
        try:
            d = cosine_distance(embed(p["image_a"]), embed(p["image_b"]))
        except (urllib.error.URLError, OSError, ValueError, KeyError) as e:
            print(f"  {p['image_a']} / {p['image_b']}: ERROR {e}", file=sys.stderr)
            continue
        results.append({"image_a": p["image_a"], "image_b": p["image_b"], "same": int(same), "distance": round(d, 4)})
        print(f"  {'SAME' if same else 'diff'}  d={d:.3f}  {p['image_a']}  {p['image_b']}")

    pos = [r["distance"] for r in results if r["same"]]
    neg = [r["distance"] for r in results if not r["same"]]
    print(f"\nSame-cat — {len(results)} pairs ({len(pos)} same, {len(neg)} different), crop={crop}")
    if pos and neg:
        print(f"  mean distance: same {sum(pos) / len(pos):.3f}   different {sum(neg) / len(neg):.3f}")
        best = None
        for t in sorted({round(x, 3) for x in pos + neg} | {0.35}):
            tp = sum(1 for d in pos if d < t)
            fp = sum(1 for d in neg if d < t)
            prec = tp / (tp + fp) if tp + fp else 0
            rec = tp / len(pos)
            f1 = 2 * prec * rec / (prec + rec) if prec + rec else 0
            if t == 0.35:
                print(f"  at current default 0.35: precision {prec:.0%} recall {rec:.0%} F1 {f1:.2f}")
            if best is None or f1 > best[3]:
                best = (t, prec, rec, f1)
        print(f"  best threshold on this set: {best[0]:.3f} (precision {best[1]:.0%}, recall {best[2]:.0%}, F1 {best[3]:.2f})")
        print("  (location / time gating is applied on top of this in the app; pairs here are image-only)")
    print(f"  per-pair results: {write_results(csv_path, results, 'same_cat')}")


def main() -> None:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = ap.add_subparsers(dest="cmd", required=True)
    e = sub.add_parser("ear-tip", help="ear-tip detection accuracy (calls OpenAI)")
    e.add_argument("csv")
    e.add_argument("--votes", type=int)
    e.add_argument("--model")
    e.add_argument("--no-head-crop", action="store_true")
    e.add_argument("--mode", choices=["judge", "main"], default="judge")
    s = sub.add_parser("same-cat", help="CLIP distance separation for same / different cat pairs (local)")
    s.add_argument("csv")
    s.add_argument("--no-crop", action="store_true", help="embed the full photo instead of the detected cat")
    args = ap.parse_args()
    run_ear_tip(args) if args.cmd == "ear-tip" else run_same_cat(args)


if __name__ == "__main__":
    main()
