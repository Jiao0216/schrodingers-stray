#!/bin/bash
# Build the Python environment for explorer.sbatch once, on a CPU node, and pack it as a single file.
#
#   sbatch eval/reid/explorer-setup.sbatch      (from /projects/schrodingers_stray/stray)
#
# pip into the shared filesystem is very slow (thousands of small files: a plain venv there took over an hour),
# so the venv is built on the node's local disk and saved as catenv.tar; each training job unpacks it to the same
# local path. PyTorch comes from the CUDA 12.8 index: the GPU nodes' driver (12.8) is too old for the default
# CUDA 13 wheels, which then silently fall back to the CPU.
#SBATCH --job-name=cat-setup
#SBATCH --partition=short
#SBATCH --cpus-per-task=4
#SBATCH --mem=16G
#SBATCH --time=02:00:00
#SBATCH --output=setup-%j.log

set -euo pipefail
cd /projects/schrodingers_stray/stray
module load anaconda3/2024.06
ENV=/tmp/catenv
rm -rf "$ENV"
python3 -m venv "$ENV"
"$ENV"/bin/pip install -q --upgrade pip
"$ENV"/bin/pip install -q torch torchvision --index-url https://download.pytorch.org/whl/cu128
"$ENV"/bin/pip install -q transformers scikit-learn pillow numpy
"$ENV"/bin/python -c "import torch; print('torch', torch.__version__, 'cuda', torch.version.cuda)"
HF_HOME=/projects/schrodingers_stray/hf "$ENV"/bin/python -c \
  "from transformers import AutoModel; AutoModel.from_pretrained('facebook/dinov2-base'); print('model ok')"
tar cf catenv.tar -C /tmp catenv
rm -rf "$ENV"
echo SETUP_DONE
