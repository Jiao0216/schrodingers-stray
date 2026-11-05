#!/usr/bin/env python3
"""Build a training list (``image,cat_id``) of individual cats for the projection head.

Two sources, both written as absolute image paths so the list can live anywhere:

    # PetFinder.my Adoption Prediction (Kaggle): train.csv + train_images/, cats only (Type == 2)
    python eval/reid/prepare.py petfinder --root ~/Downloads/petfinder-adoption-prediction

    # Your own folders: one sub-folder per cat (e.g. frames from a video), named after the cat
    python eval/reid/prepare.py folders --root ~/cat_frames

Cats with fewer than ``--min-photos`` photos are skipped (a single photo cannot teach "same cat"). cat_ids are
prefixed with the source (``pf:``, ``dir:``) so they can never collide with the evaluation set's ids.
Never put the evaluation cats (eval/cats.csv) in here: train_head.py also drops near-identical photos.
"""

from __future__ import annotations

import argparse
import csv
import random
from collections import defaultdict
from pathlib import Path

IMAGE_EXTS = {".jpg", ".jpeg", ".png", ".webp"}
DEFAULT_OUT = Path(__file__).resolve().parent / "data" / "train_cats.csv"


def from_petfinder(root: Path) -> dict[str, list[Path]]:
    cats = set()
    with (root / "train.csv").open(newline="", encoding="utf-8") as f:
        for row in csv.DictReader(f):
            if row.get("Type", "").strip() == "2":
                cats.add(row["PetID"].strip())
    groups: dict[str, list[Path]] = defaultdict(list)
    for img in sorted((root / "train_images").glob("*.jpg")):
        pet_id = img.stem.rsplit("-", 1)[0]  # train_images/<PetID>-<n>.jpg
        if pet_id in cats:
            groups[f"pf:{pet_id}"].append(img.resolve())
    return groups


def from_folders(root: Path) -> dict[str, list[Path]]:
    groups: dict[str, list[Path]] = {}
    for d in sorted(p for p in root.iterdir() if p.is_dir()):
        imgs = sorted(p.resolve() for p in d.iterdir() if p.suffix.lower() in IMAGE_EXTS)
        if imgs:
            groups[f"dir:{d.name}"] = imgs
    return groups


def main() -> None:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("source", choices=["petfinder", "folders"])
    ap.add_argument("--root", required=True, type=Path)
    ap.add_argument("--out", type=Path, default=DEFAULT_OUT)
    ap.add_argument("--min-photos", type=int, default=2)
    ap.add_argument("--max-photos", type=int, default=6, help="cap per cat so a few cats don't dominate")
    ap.add_argument("--max-cats", type=int, default=0, help="0 = all; e.g. 500 for a quick first run")
    args = ap.parse_args()

    root = args.root.expanduser()
    groups = from_petfinder(root) if args.source == "petfinder" else from_folders(root)
    kept = {cid: imgs[: args.max_photos] for cid, imgs in groups.items() if len(imgs) >= args.min_photos}
    ids = sorted(kept)
    if args.max_cats:
        random.Random(0).shuffle(ids)
        ids = sorted(ids[: args.max_cats])

    args.out.parent.mkdir(parents=True, exist_ok=True)
    with args.out.open("w", newline="", encoding="utf-8") as f:
        w = csv.writer(f)
        w.writerow(["image", "cat_id"])
        for cid in ids:
            for img in kept[cid]:
                w.writerow([str(img), cid])
    n_photos = sum(len(kept[c]) for c in ids)
    print(f"{len(ids)} cats, {n_photos} photos ({len(groups) - len(kept)} cats with < {args.min_photos} photos skipped)")
    print(f"wrote {args.out}")


if __name__ == "__main__":
    main()
