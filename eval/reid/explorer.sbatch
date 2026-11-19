#!/bin/bash
# Fine-tune DINOv2 for same-cat matching on Northeastern's Explorer cluster.
#
# One-time setup, in /projects/schrodingers_stray/stray (ssh explorer):
#   copy eval/baseline.py, eval/run_eval.py, eval/reid/*.py and embedding-service/detector.py here (same layout
#   as the repo), plus the crops as one file: `tar cf crops.tar crops` in eval/reid/data, uploaded through the
#   transfer node (scp ... xfer.discovery.neu.edu:/projects/schrodingers_stray/stray/). Then build the
#   environment with `sbatch eval/reid/explorer-setup.sbatch` (writes catenv.tar).
# Then submit from that folder:
#   sbatch eval/reid/explorer.sbatch
# The gpu partition allows 8 h per job; check partitions with `sinfo` (course accounts may use courses-gpu).
#SBATCH --job-name=cat-reid
#SBATCH --partition=gpu
#SBATCH --gres=gpu:h200:1
#SBATCH --cpus-per-task=8
#SBATCH --mem=32G
#SBATCH --time=08:00:00
#SBATCH --output=cat-reid-%j.log

set -euo pipefail
cd /projects/schrodingers_stray/stray
export HF_HOME=/projects/schrodingers_stray/hf  # model cache outside the small home quota
nvidia-smi --query-gpu=name,memory.total,driver_version --format=csv

# Small files are slow on the shared filesystem: unpack the environment and the 15k JPEG crops onto the node's
# local disk. The environment must sit at /tmp/catenv, the path it was built at.
CROPS=${TMPDIR:-/tmp}/cat-reid-$SLURM_JOB_ID
mkdir -p "$CROPS" && tar xf crops.tar -C "$CROPS"
rm -rf /tmp/catenv && tar xf catenv.tar -C /tmp
trap 'rm -rf "$CROPS" /tmp/catenv' EXIT
PY=/tmp/catenv/bin/python
$PY -c "import torch, sys; ok = torch.cuda.is_available(); print('cuda', ok); sys.exit(0 if ok else 'no GPU visible to PyTorch')"

$PY eval/reid/finetune.py \
  "$CROPS"/crops/train/crops.csv "$CROPS"/crops/eval/crops.csv \
  --unfreeze 4 --epochs 20 --workers 8
