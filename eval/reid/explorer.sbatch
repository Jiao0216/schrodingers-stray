#!/bin/bash
# Fine-tune DINOv2 for same-cat matching on Northeastern's Explorer cluster.
#
# Copy the repo's eval/ and embedding-service/ folders plus eval/reid/data/crops/ to /scratch/$USER/stray, then:
#   sbatch eval/reid/explorer.sbatch
# Course accounts use the courses-gpu partition; personal accounts use gpu. Check partition names and time
# limits in the Explorer docs (rc-docs.northeastern.edu) before submitting.
#SBATCH --job-name=cat-reid
#SBATCH --partition=courses-gpu
#SBATCH --gres=gpu:1
#SBATCH --cpus-per-task=8
#SBATCH --mem=32G
#SBATCH --time=04:00:00
#SBATCH --output=cat-reid-%j.log

set -euo pipefail
cd /scratch/$USER/stray
module load anaconda3 2>/dev/null || true
if [ ! -d .venv ]; then
  python3 -m venv .venv
  .venv/bin/pip install -q torch torchvision transformers scikit-learn pillow numpy
fi

.venv/bin/python eval/reid/finetune.py \
  eval/reid/data/crops/train/crops.csv eval/reid/data/crops/eval/crops.csv \
  --unfreeze 4 --epochs 20 --workers 8
