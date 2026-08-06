"""CLIP image embedding microservice for stray-cat same-cat identification."""

from __future__ import annotations

import io
import logging
from contextlib import asynccontextmanager
from typing import Annotated

import numpy as np
from fastapi import FastAPI, File, HTTPException, UploadFile
from fastapi.responses import JSONResponse
from PIL import Image, UnidentifiedImageError
from pydantic import BaseModel, Field
from sentence_transformers import SentenceTransformer

from chroma_store import collection_count, collection_name, get_collection, persist_dir

logger = logging.getLogger(__name__)
logging.basicConfig(level=logging.INFO)

MODEL_NAME = "clip-ViT-B-32"
ALLOWED_MIME_PREFIX = "image/"
MAX_BYTES = 15 * 1024 * 1024
EXPECTED_DIM = 512

model: SentenceTransformer | None = None


@asynccontextmanager
async def lifespan(app: FastAPI):
    global model
    logger.info("Loading CLIP model %s ...", MODEL_NAME)
    model = SentenceTransformer(MODEL_NAME)
    get_collection()
    logger.info("Model and Chroma collection %s ready (persist=%s)", collection_name(), persist_dir())
    yield
    model = None


app = FastAPI(title="Cat CLIP Embedding Service", lifespan=lifespan)


class ChromaAddRequest(BaseModel):
    id: str
    embedding: list[float]
    sighting_id: int
    cat_id: int | None = None
    upload_time: str
    location_approx: str
    image_url: str = ""


class ChromaQueryRequest(BaseModel):
    embedding: list[float]
    top_k: int = Field(default=5, ge=1, le=50)
    exclude_id: str | None = None


def embed_image_bytes(raw: bytes) -> list[float]:
    if model is None:
        raise RuntimeError("Model not loaded")
    try:
        image = Image.open(io.BytesIO(raw)).convert("RGB")
    except UnidentifiedImageError as exc:
        raise ValueError("Unrecognized or corrupted image file") from exc
    vec = model.encode(image)
    arr = np.asarray(vec, dtype=np.float32).reshape(-1)
    return arr.tolist()


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
        "chroma": {
            "collection": collection_name(),
            "persist_dir": persist_dir(),
            "count": collection_count(),
        },
    }


@app.post("/embed")
async def embed(file: Annotated[UploadFile, File(...)]):
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

    try:
        embedding = embed_image_bytes(raw)
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
        }
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
