#!/bin/bash
# Fine-tune DINOv2 for same-cat matching on Northeastern's Explorer cluster.
#
# One-time setup on a login node (ssh explorer), in /projects/schrodingers_stray/stray:
#   copy eval/baseline.py, eval/run_eval.py, eval/reid/*.py and embedding-service/detector.py here (same layout
#   as the repo), plus the crops as one file: `tar cf crops.tar crops` in eval/reid/data, uploaded through the
#   transfer node (scp ... xfer.discovery.neu.edu:/projects/schrodingers_stray/stray/). Then
#   module load anaconda3/2024.06 && python3 -m venv .venv
#   .venv/bin/pip install torch torchvision transformers scikit-learn pillow numpy
# Then submit from that folder:
#   sbatch eval/reid/explorer.sbatch
# The gpu partition allows 8 h per job; check partitions with `sinfo` (course accounts may use courses-gpu).
#SBATCH --job-name=cat-reid
#SBATCH --partition=gpu
#SBATCH --gres=gpu:1
#SBATCH --cpus-per-task=8
#SBATCH --mem=32G
#SBATCH --time=08:00:00
#SBATCH --output=cat-reid-%j.log

set -euo pipefail
cd /projects/schrodingers_stray/stray
export HF_HOME=/projects/schrodingers_stray/hf  # model cache outside the small home quota
nvidia-smi --query-gpu=name,memory.total --format=csv

# 15k small JPEGs are slow on the shared filesystem: unpack them onto the node's local disk for the run.
CROPS=${TMPDIR:-/tmp}/cat-reid-$SLURM_JOB_ID
mkdir -p "$CROPS" && tar xf crops.tar -C "$CROPS"
trap 'rm -rf "$CROPS"' EXIT

.venv/bin/python eval/reid/finetune.py \
  "$CROPS"/crops/train/crops.csv "$CROPS"/crops/eval/crops.csv \
  --unfreeze 4 --epochs 20 --workers 8
