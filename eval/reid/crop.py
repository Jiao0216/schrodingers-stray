#!/usr/bin/env python3
"""Crop every photo to the detected cat once (OWL-ViT, square padding) and save small JPEGs for fine-tuning.

The detector is the slow part of the pipeline (~2 photos/s on a Mac CPU), so fine-tuning reads these crops
instead of re-detecting every epoch. Run it for the training list and for the evaluation set:

    embedding-service/.venv/bin/python eval/reid/crop.py eval/reid/data/train_cats.csv eval/reid/data/crops/train
    embedding-service/.venv/bin/python eval/reid/crop.py eval/cats.csv eval/reid/data/crops/eval

Each output folder gets a ``crops.csv`` (``image,cat_id``) next to the JPEGs. Re-running skips existing crops,
so an interrupted run can simply be started again.
"""

from __future__ import annotations

import argparse
import csv
import hashlib
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
import baseline  # noqa: E402  (same crop + square padding as embedding-service)
from run_eval import read_csv  # noqa: E402

SIDE = 288  # a little above DINOv2's 256 resize, leaving room for random-crop augmentation


def main() -> None:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("csv", type=Path)
    ap.add_argument("out_dir", type=Path)
    args = ap.parse_args()

    rows = read_csv(args.csv)
    args.out_dir.mkdir(parents=True, exist_ok=True)
    out_rows, fallbacks, start, done = [], 0, time.time(), 0
    for i, r in enumerate(rows, 1):
        src = (args.csv.parent / r["image"].strip()).resolve()
        dst = args.out_dir / f"{hashlib.sha1(str(src).encode()).hexdigest()[:16]}.jpg"
        if not dst.exists():
            crops, fb = baseline.prepare([src], "cat")
            crops[0].resize((SIDE, SIDE)).save(dst, quality=92)
            fallbacks += fb
            done += 1
        out_rows.append((dst.name, r["cat_id"].strip()))
        if i % 200 == 0 or i == len(rows):
            rate = done / max(1e-6, time.time() - start)
            left = (len(rows) - i) / rate / 60 if rate else 0
            print(f"  {i}/{len(rows)} photos ({rate:.1f}/s, ~{left:.0f} min left)", flush=True)

    with (args.out_dir / "crops.csv").open("w", newline="", encoding="utf-8") as f:
        w = csv.writer(f)
        w.writerow(["image", "cat_id"])
        w.writerows(out_rows)
    print(f"{len(out_rows)} crops ({done} new, {fallbacks} without a detected cat) -> {args.out_dir / 'crops.csv'}")


if __name__ == "__main__":
    main()
