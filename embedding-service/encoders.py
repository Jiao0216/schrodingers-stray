"""Image encoders for same-cat matching. Pick one with CAT_EMBEDDING_MODEL (dinov2 | clip).

DINOv2 is the default: on labelled photos (eval/baseline.py) it separated individual cats far better than CLIP,
whose distances for same and different cats overlapped heavily. CLIP stays available as a fallback.
"""

from __future__ import annotations

import os
from dataclasses import dataclass

import numpy as np
import torch
from PIL import Image


@dataclass(frozen=True)
class EncoderSpec:
    name: str
    model_id: str
    dim: int
    version_prefix: str


SPECS = {
    "dinov2": EncoderSpec("dinov2", "facebook/dinov2-base", 768, "dinov2-b"),
    "clip": EncoderSpec("clip", "clip-ViT-B-32", 512, "clip-b32"),
}

EMBEDDING_MODEL = os.environ.get("CAT_EMBEDDING_MODEL", "dinov2").strip().lower()
if EMBEDDING_MODEL not in SPECS:
    raise ValueError(f"CAT_EMBEDDING_MODEL must be one of {sorted(SPECS)}, got {EMBEDDING_MODEL!r}")
SPEC = SPECS[EMBEDDING_MODEL]


def square(img: Image.Image) -> Image.Image:
    """Pad to a square so the processor's center crop cannot cut off ear tips or tails."""
    side = max(img.size)
    out = Image.new("RGB", (side, side), (124, 116, 104))
    out.paste(img, ((side - img.width) // 2, (side - img.height) // 2))
    return out


class ClipEncoder:
    def __init__(self) -> None:
        from sentence_transformers import SentenceTransformer

        self._model = SentenceTransformer(SPECS["clip"].model_id)

    def encode(self, image: Image.Image) -> np.ndarray:
        return np.asarray(self._model.encode(image), dtype=np.float32).reshape(-1)


class Dinov2Encoder:
    def __init__(self) -> None:
        from transformers import AutoImageProcessor, AutoModel

        model_id = SPECS["dinov2"].model_id
        self._processor = AutoImageProcessor.from_pretrained(model_id)
        self._model = AutoModel.from_pretrained(model_id).eval()

    @torch.no_grad()
    def encode(self, image: Image.Image) -> np.ndarray:
        pixels = self._processor(images=square(image), return_tensors="pt")["pixel_values"]
        return self._model(pixel_values=pixels).pooler_output[0].float().numpy()  # CLS token


def load() -> ClipEncoder | Dinov2Encoder:
    return Dinov2Encoder() if EMBEDDING_MODEL == "dinov2" else ClipEncoder()
