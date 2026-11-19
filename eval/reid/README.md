# Projection head for same-cat matching

Trains a small head on top of **frozen** DINOv2 vectors (768 → 512 → 256) so that photos of the same cat land
closer together, then compares it with plain DINOv2 on the evaluation set (`eval/cats.csv`). Everything runs on
a laptop: DINOv2 vectors are computed once, and training only touches those vectors.

Run from the repo root with the embedding-service virtualenv (`P=embedding-service/.venv/bin/python`).

## 1. Training data: cats that are *not* in the evaluation set

**PetFinder.my** (Kaggle, several photos per pet): sign in to Kaggle, accept the rules of the
"PetFinder.my Adoption Prediction" competition, download and unzip it. Then:

```bash
$P eval/reid/prepare.py petfinder --root ~/Downloads/petfinder-adoption-prediction --max-cats 1000
```

**Your own folders** (for example frames from short videos), one sub-folder per cat:

```bash
$P eval/reid/prepare.py folders --root ~/cat_frames
```

Both write `eval/reid/data/train_cats.csv`. Never add the evaluation cats here; `train_head.py` also drops
training photos that are near-copies of an evaluation photo.

## 2. Precompute DINOv2 vectors

```bash
$P eval/reid/embed.py eval/reid/data/train_cats.csv eval/reid/data/train.npz
$P eval/reid/embed.py eval/cats.csv eval/reid/data/eval.npz
```

Same pipeline as embedding-service (OWL-ViT cat crop, square padding, DINOv2 CLS). The cat detector runs at
about 2 photos/s on a Mac CPU, so 1000 PetFinder cats (~4000 photos) take roughly 30–40 minutes.

## 3. Train and compare

```bash
$P eval/reid/train_head.py eval/reid/data/train.npz eval/reid/data/eval.npz
```

Training cats are split again by cat into train / validation. Early stopping and the match threshold (for
`--target-recall`, default 80%) are chosen on validation only; the evaluation set is scored once at the end:

```
  model         rank-1    mAP    AUC   threshold  recall  false merges
  dinov2          ...
  dinov2+head     ...
```

The head is worth deploying only if it has **fewer false merges at the same or better recall** than plain
DINOv2. Deploying it means applying it inside embedding-service after DINOv2, with a new `embedding_version`,
a new Chroma collection and a recalibrated threshold. The saved `eval/reid/data/head.pt` records the input
format and the validation threshold.

## 4. Larger run: fine-tune the last DINOv2 blocks

`finetune.py` also trains the last `--unfreeze` DINOv2 blocks (default 2) on augmented crops, so it needs a GPU.
Crop every photo once first (the detector is the slow part), then train:

```bash
$P eval/reid/crop.py eval/reid/data/train_cats.csv eval/reid/data/crops/train
$P eval/reid/crop.py eval/cats.csv eval/reid/data/crops/eval
$P eval/reid/finetune.py eval/reid/data/crops/train/crops.csv eval/reid/data/crops/eval/crops.csv
```

It reports plain DINOv2 and the fine-tuned model side by side on the evaluation set, with the same rules as
step 3 (validation-only threshold and early stopping).

Measured speeds:

| Where | Speed | All single-cat PetFinder listings (3,893 cats, 15,387 photos), 15 epochs |
|---|---|---|
| M1 MacBook, 8 GB, `--p 8 --k 4` | ~7 photos/s (batch 128 does not fit in memory) | ~8 h of training plus ~2 h of cropping: use `--max-cats 1000` in step 1 instead |
| Explorer, one H200 (`explorer.sbatch`, `--p 32 --k 4`) | ~61 s per epoch | 17 epochs (early stop, best at 13) in ~17 min; validation mAP 0.49 → 0.64 |

On Explorer (SSH needs the key Research Computing puts in `~/.ssh` on the cluster; download it from Open
OnDemand): follow the one-time setup at the top of `explorer.sbatch`. Upload the crops as a single `crops.tar`
through the transfer node: thousands of small files over the login node crawl at a few files per second. Build
the environment once with `sbatch eval/reid/explorer-setup.sbatch`: it builds the venv on a `short` node's local
disk and packs it as `catenv.tar` (pip into `/projects` directly took over an hour), and installs PyTorch from
the CUDA 12.8 index, because the GPU nodes' driver is too old for the default CUDA 13 wheels, which silently run
on the CPU. Then `sbatch eval/reid/explorer.sbatch` from `/projects/schrodingers_stray/stray`; it unpacks
`catenv.tar` and the crops onto the node and exits early if PyTorch cannot see the GPU.
