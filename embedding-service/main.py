"""CLIP image embedding microservice for stray-cat same-cat identification."""

from __future__ import annotations

import io
import logging
import os
from contextlib import asynccontextmanager
from typing import Annotated

import numpy as np
from fastapi import FastAPI, File, HTTPException, Query, UploadFile
from fastapi.responses import JSONResponse, Response
from PIL import Image, UnidentifiedImageError
from pydantic import BaseModel, Field
from sentence_transformers import SentenceTransformer

import detector
from chroma_store import collection_count, collection_name, get_collection, persist_dir

logger = logging.getLogger(__name__)
logging.basicConfig(level=logging.INFO)

MODEL_NAME = "clip-ViT-B-32"
ALLOWED_MIME_PREFIX = "image/"
MAX_BYTES = 15 * 1024 * 1024
EXPECTED_DIM = 512

# Crop to the detected cat before CLIP so background does not dominate the embedding.
CROP_ENABLED = os.environ.get("CAT_CROP_ENABLED", "true").strip().lower() not in ("0", "false", "no")
# Vectors from different pipelines are not comparable; queries only match the current version.
EMBEDDING_VERSION = "clip-b32-catcrop-v1" if CROP_ENABLED else "clip-b32-full-v1"

model: SentenceTransformer | None = None


@asynccontextmanager
async def lifespan(app: FastAPI):
    global model
    logger.info("Loading CLIP model %s ...", MODEL_NAME)
    model = SentenceTransformer(MODEL_NAME)
    if CROP_ENABLED:
        detector.load()
    get_collection()
    logger.info(
        "Model and Chroma collection %s ready (persist=%s, embedding_version=%s)",
        collection_name(),
        persist_dir(),
        EMBEDDING_VERSION,
    )
    yield
    model = None
    detector.unload()


app = FastAPI(title="Cat CLIP Embedding Service", lifespan=lifespan)


class ChromaAddRequest(BaseModel):
    id: str
    embedding: list[float]
    sighting_id: int
    cat_id: int | None = None
    upload_time: str
    location_approx: str
    image_url: str = ""
    embedding_version: str | None = None


class ChromaQueryRequest(BaseModel):
    embedding: list[float]
    top_k: int = Field(default=5, ge=1, le=50)
    exclude_id: str | None = None
    embedding_version: str | None = None


def open_image(raw: bytes) -> Image.Image:
    try:
        return Image.open(io.BytesIO(raw)).convert("RGB")
    except UnidentifiedImageError as exc:
        raise ValueError("Unrecognized or corrupted image file") from exc


def embed_image_bytes(raw: bytes, crop: bool = True) -> tuple[list[float], dict]:
    """CLIP embedding, computed on the detected cat crop when available. Returns (embedding, crop info)."""
    if model is None:
        raise RuntimeError("Model not loaded")
    image = open_image(raw)
    crop_info: dict = {"applied": False}
    if crop and CROP_ENABLED and detector.is_loaded():
        found = detector.detect(image)
        if found.cat is not None:
            image = detector.crop_cat(image, found.cat)
            crop_info = {"applied": True, "box": found.cat.as_list(), "score": round(found.cat.score, 4)}
    vec = model.encode(image)
    arr = np.asarray(vec, dtype=np.float32).reshape(-1)
    return arr.tolist(), crop_info


async def _read_image_upload(file: UploadFile) -> bytes:
    if file.content_type and not file.content_type.startswith(ALLOWED_MIME_PREFIX):
        raise HTTPException(
            status_code=415,
            detail=f"Unsupported content type: {file.content_type}. Expected image/*.",
        )
    raw = await file.read()
    if not raw:
        raise HTTPException(status_code=400, detail="Empty file")
    if len(raw) > MAX_BYTES:
        raise HTTPException(status_code=413, detail=f"File exceeds {MAX_BYTES} bytes")
    return raw


def _validate_embedding(embedding: list[float]) -> None:
    if not embedding:
        raise HTTPException(status_code=400, detail="embedding must not be empty")
    if len(embedding) != EXPECTED_DIM:
        raise HTTPException(
            status_code=400,
            detail=f"embedding must have {EXPECTED_DIM} dimensions, got {len(embedding)}",
        )


def _metadata_from_request(req: ChromaAddRequest) -> dict:
    return {
        "sighting_id": req.sighting_id,
        "cat_id": req.cat_id if req.cat_id is not None else -1,
        "upload_time": req.upload_time,
        "location_approx": req.location_approx,
        "image_url": req.image_url or "",
        "embedding_version": req.embedding_version or EMBEDDING_VERSION,
    }


def _match_from_row(row_id: str, metadata: dict, distance: float) -> dict:
    cat_raw = metadata.get("cat_id", -1)
    cat_id = None if cat_raw is None or int(cat_raw) < 0 else int(cat_raw)
    return {
        "id": row_id,
        "sighting_id": int(metadata.get("sighting_id", -1)),
        "cat_id": cat_id,
        "distance": float(distance),
        "upload_time": str(metadata.get("upload_time", "")),
        "location_approx": str(metadata.get("location_approx", "")),
        "image_url": str(metadata.get("image_url", "")),
    }


@app.get("/health")
def health():
    return {
        "status": "ok",
        "model": MODEL_NAME,
        "ready": model is not None,
        "embedding_version": EMBEDDING_VERSION,
        "detector": {
            "enabled": CROP_ENABLED,
            "model": detector.DETECTOR_MODEL if CROP_ENABLED else None,
            "ready": detector.is_loaded(),
        },
        "chroma": {
            "collection": collection_name(),
            "persist_dir": persist_dir(),
            "count": collection_count(),
        },
    }


@app.post("/embed")
async def embed(
    file: Annotated[UploadFile, File(...)],
    crop: Annotated[bool, Query(description="Crop to the detected cat before embedding")] = True,
):
    raw = await _read_image_upload(file)

    try:
        embedding, crop_info = embed_image_bytes(raw, crop=crop)
    except ValueError as exc:
        raise HTTPException(status_code=422, detail=str(exc)) from exc
    except Exception as exc:
        logger.exception("Embedding computation failed")
        raise HTTPException(status_code=500, detail="Embedding computation failed") from exc

    return JSONResponse(
        {
            "embedding": embedding,
            "dimensions": len(embedding),
            "model": MODEL_NAME,
            "embedding_version": EMBEDDING_VERSION,
            "crop": crop_info,
        }
    )


def _detect_or_503(image: Image.Image) -> detector.Detection:
    if not detector.is_loaded():
        raise HTTPException(status_code=503, detail="Cat detector disabled (CAT_CROP_ENABLED=false) or not loaded")
    try:
        return detector.detect(image)
    except Exception as exc:
        logger.exception("Cat detection failed")
        raise HTTPException(status_code=500, detail="Cat detection failed") from exc


@app.post("/detect")
async def detect(file: Annotated[UploadFile, File(...)]):
    raw = await _read_image_upload(file)
    try:
        image = open_image(raw)
    except ValueError as exc:
        raise HTTPException(status_code=422, detail=str(exc)) from exc
    found = _detect_or_503(image)

    def box_json(b: detector.Box | None):
        return None if b is None else {"box": b.as_list(), "score": round(b.score, 4)}

    return {"width": image.width, "height": image.height, "cat": box_json(found.cat), "head": box_json(found.head)}


@app.post("/crop")
async def crop(
    file: Annotated[UploadFile, File(...)],
    target: Annotated[str, Query(pattern="^(cat|head)$")] = "head",
):
    """JPEG crop of the detected cat or (padded, upscaled) cat head; 404 when nothing is detected."""
    raw = await _read_image_upload(file)
    try:
        image = open_image(raw)
    except ValueError as exc:
        raise HTTPException(status_code=422, detail=str(exc)) from exc
    found = _detect_or_503(image)

    box = found.head if target == "head" else found.cat
    if box is None:
        raise HTTPException(status_code=404, detail=f"No {target} detected")
    cropped = detector.crop_head(image, box) if target == "head" else detector.crop_cat(image, box)

    buf = io.BytesIO()
    cropped.save(buf, format="JPEG", quality=92)
    return Response(
        content=buf.getvalue(),
        media_type="image/jpeg",
        headers={"X-Crop-Box": ",".join(str(v) for v in box.as_list()), "X-Crop-Score": f"{box.score:.4f}"},
    )


@app.post("/chroma/add")
def chroma_add(req: ChromaAddRequest):
    _validate_embedding(req.embedding)
    if not req.id or not req.id.strip():
        raise HTTPException(status_code=400, detail="id is required")

    collection = get_collection()
    try:
        collection.upsert(
            ids=[req.id.strip()],
            embeddings=[req.embedding],
            metadatas=[_metadata_from_request(req)],
        )
    except Exception as exc:
        logger.exception("Chroma add failed for id=%s", req.id)
        raise HTTPException(status_code=500, detail="Chroma add failed") from exc

    return {"ok": True, "id": req.id.strip(), "collection": collection_name()}


@app.post("/chroma/query")
def chroma_query(req: ChromaQueryRequest):
    _validate_embedding(req.embedding)
    collection = get_collection()
    if collection.count() == 0:
        return {"matches": []}

    fetch_k = req.top_k + (1 if req.exclude_id else 0)
    try:
        result = collection.query(
            query_embeddings=[req.embedding],
            n_results=min(fetch_k, collection.count()),
            where={"embedding_version": req.embedding_version or EMBEDDING_VERSION},
            include=["metadatas", "distances"],
        )
    except Exception as exc:
        logger.exception("Chroma query failed")
        raise HTTPException(status_code=500, detail="Chroma query failed") from exc

    ids = result.get("ids", [[]])[0]
    metadatas = result.get("metadatas", [[]])[0]
    distances = result.get("distances", [[]])[0]

    matches: list[dict] = []
    for row_id, metadata, distance in zip(ids, metadatas, distances):
        if req.exclude_id and row_id == req.exclude_id:
            continue
        matches.append(_match_from_row(row_id, metadata or {}, distance))
        if len(matches) >= req.top_k:
            break

    return {"matches": matches}


@app.get("/chroma/stats")
def chroma_stats():
    """How many stored vectors match the current embedding version (the rest need a reindex)."""
    collection = get_collection()
    total = collection.count()
    current = len(collection.get(where={"embedding_version": EMBEDDING_VERSION}, include=[])["ids"]) if total else 0
    return {"collection": collection_name(), "embedding_version": EMBEDDING_VERSION, "total": total, "current_version": current}
