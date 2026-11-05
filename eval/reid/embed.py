#!/usr/bin/env python3
"""Precompute frozen DINOv2 vectors for an ``image,cat_id`` list, so training the head never touches images.

Uses the same pipeline as embedding-service: OWL-ViT cat crop, square padding, DINOv2-base CLS token
(L2-normalised here). Run it once for the training list and once for the evaluation set:

    embedding-service/.venv/bin/python eval/reid/embed.py eval/reid/data/train_cats.csv eval/reid/data/train.npz
    embedding-service/.venv/bin/python eval/reid/embed.py eval/cats.csv eval/reid/data/eval.npz

Image paths are relative to the CSV (absolute paths also work).
"""

from __future__ import annotations

import argparse
import sys
import time
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
import baseline  # noqa: E402  (shared crop + backbone code)
from run_eval import read_csv  # noqa: E402

CHUNK = 64  # images cropped and embedded per step; keeps memory flat on large lists


def main() -> None:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("csv", type=Path)
    ap.add_argument("out", type=Path)
    ap.add_argument("--crop", choices=["cat", "none"], default="cat")
    args = ap.parse_args()

    rows = read_csv(args.csv)
    paths = [(args.csv.parent / r["image"].strip()).resolve() for r in rows]
    ids = np.array([r["cat_id"].strip() for r in rows])
    backbone = baseline.Backbone("dinov2")

    feats, fallbacks, start = [], 0, time.time()
    for i in range(0, len(paths), CHUNK):
        crops, fb = baseline.prepare(paths[i:i + CHUNK], args.crop)
        feats.append(backbone.embed(crops))
        fallbacks += fb
        done = min(i + CHUNK, len(paths))
        rate = done / max(1e-6, time.time() - start)
        print(f"  {done}/{len(paths)} photos  ({rate:.1f}/s, ~{(len(paths) - done) / rate / 60:.0f} min left)", flush=True)

    args.out.parent.mkdir(parents=True, exist_ok=True)
    np.savez_compressed(args.out, feats=np.concatenate(feats).astype(np.float32), cat_ids=ids,
                        images=np.array([str(p) for p in paths]), crop=args.crop)
    print(f"{len(paths)} photos of {len(set(ids))} cats ({fallbacks} without a detected cat) -> {args.out}")


if __name__ == "__main__":
    main()
