"""Open-vocabulary cat / cat-head detection (OWL-ViT) used to crop photos before CLIP and ear-tip checks."""

from __future__ import annotations

import logging
import os
from dataclasses import dataclass

import torch
from PIL import Image
from transformers import AutoProcessor, OwlViTForObjectDetection

logger = logging.getLogger(__name__)

DETECTOR_MODEL = os.environ.get("CAT_DETECTOR_MODEL", "google/owlvit-base-patch32")
QUERIES = ["a photo of a cat", "a photo of a cat's head"]
CAT_LABEL, HEAD_LABEL = 0, 1

# OWL-ViT scores are not calibrated probabilities: cats typically score 0.3-0.9, heads 0.05-0.3.
CAT_MIN_SCORE = float(os.environ.get("CAT_DETECTOR_CAT_MIN_SCORE", "0.15"))
HEAD_MIN_SCORE = float(os.environ.get("CAT_DETECTOR_HEAD_MIN_SCORE", "0.05"))

# Head boxes hug the face and clip the ear tips, so pad generously (extra on top).
CAT_PAD = 0.08
HEAD_PAD_SIDE = 0.20
HEAD_PAD_TOP = 0.35
HEAD_PAD_BOTTOM = 0.10
HEAD_MIN_SIDE_PX = 512
HEAD_MAX_SIDE_PX = 1024

_processor = None
_model = None


@dataclass(frozen=True)
class Box:
    x0: float
    y0: float
    x1: float
    y1: float
    score: float

    def as_list(self) -> list[int]:
        return [round(self.x0), round(self.y0), round(self.x1), round(self.y1)]

    def center_inside(self, other: "Box") -> bool:
        cx, cy = (self.x0 + self.x1) / 2, (self.y0 + self.y1) / 2
        return other.x0 <= cx <= other.x1 and other.y0 <= cy <= other.y1


@dataclass(frozen=True)
class Detection:
    cat: Box | None
    head: Box | None


def load() -> None:
    global _processor, _model
    logger.info("Loading cat detector %s ...", DETECTOR_MODEL)
    _processor = AutoProcessor.from_pretrained(DETECTOR_MODEL)
    _model = OwlViTForObjectDetection.from_pretrained(DETECTOR_MODEL).eval()


def unload() -> None:
    global _processor, _model
    _processor = None
    _model = None


def is_loaded() -> bool:
    return _model is not None


def detect(image: Image.Image) -> Detection:
    """Best cat box, and the best head box whose center lies inside that cat (or anywhere if no cat)."""
    if _model is None or _processor is None:
        raise RuntimeError("Detector not loaded")
    width, height = image.size
    inputs = _processor(text=[QUERIES], images=image, return_tensors="pt")
    with torch.no_grad():
        outputs = _model(**inputs)
    result = _processor.post_process_grounded_object_detection(
        outputs,
        threshold=min(CAT_MIN_SCORE, HEAD_MIN_SCORE),
        target_sizes=torch.tensor([[height, width]]),
    )[0]

    cats: list[Box] = []
    heads: list[Box] = []
    for score, label, box in zip(result["scores"], result["labels"], result["boxes"]):
        x0, y0, x1, y1 = (float(v) for v in box.tolist())
        b = Box(max(0.0, x0), max(0.0, y0), min(float(width), x1), min(float(height), y1), float(score))
        if b.x1 <= b.x0 or b.y1 <= b.y0:
            continue
        if int(label) == CAT_LABEL and b.score >= CAT_MIN_SCORE:
            cats.append(b)
        elif int(label) == HEAD_LABEL and b.score >= HEAD_MIN_SCORE:
            heads.append(b)

    cat = max(cats, key=lambda b: b.score, default=None)
    if cat is not None:
        heads = [h for h in heads if h.center_inside(cat)]
    head = max(heads, key=lambda b: b.score, default=None)
    return Detection(cat=cat, head=head)


def _padded(box: Box, image: Image.Image, left: float, top: float, right: float, bottom: float) -> tuple[int, int, int, int]:
    w, h = box.x1 - box.x0, box.y1 - box.y0
    return (
        max(0, round(box.x0 - w * left)),
        max(0, round(box.y0 - h * top)),
        min(image.width, round(box.x1 + w * right)),
        min(image.height, round(box.y1 + h * bottom)),
    )


def crop_cat(image: Image.Image, box: Box) -> Image.Image:
    return image.crop(_padded(box, image, CAT_PAD, CAT_PAD, CAT_PAD, CAT_PAD))


def crop_head(image: Image.Image, box: Box) -> Image.Image:
    """Padded head crop, upscaled so the short side is at least HEAD_MIN_SIDE_PX for ear-tip inspection."""
    crop = image.crop(_padded(box, image, HEAD_PAD_SIDE, HEAD_PAD_TOP, HEAD_PAD_SIDE, HEAD_PAD_BOTTOM))
    short_side, long_side = min(crop.size), max(crop.size)
    scale = 1.0
    if short_side < HEAD_MIN_SIDE_PX:
        scale = HEAD_MIN_SIDE_PX / short_side
    if long_side * scale > HEAD_MAX_SIDE_PX:
        scale = HEAD_MAX_SIDE_PX / long_side
    if abs(scale - 1.0) > 0.01:
        crop = crop.resize((round(crop.width * scale), round(crop.height * scale)), Image.LANCZOS)
    return crop
