"""Local ChromaDB persist store for stray cat image embeddings."""

from __future__ import annotations

import os
from pathlib import Path

import chromadb

from encoders import EMBEDDING_MODEL

PERSIST_DIR = Path(os.environ.get("CHROMA_PERSIST_DIR", "./chroma_db")).resolve()
# One collection per encoder: Chroma fixes the vector size per collection (CLIP 512, DINOv2 768). CLIP keeps the
# original name so existing vectors stay usable when switching back.
COLLECTION_NAME = os.environ.get("CHROMA_COLLECTION") or (
    "stray_cats" if EMBEDDING_MODEL == "clip" else f"stray_cats_{EMBEDDING_MODEL}"
)

_client: chromadb.PersistentClient | None = None
_collection = None


def get_collection():
    global _client, _collection
    if _collection is None:
        PERSIST_DIR.mkdir(parents=True, exist_ok=True)
        _client = chromadb.PersistentClient(path=str(PERSIST_DIR))
        _collection = _client.get_or_create_collection(
            name=COLLECTION_NAME,
            metadata={"hnsw:space": "cosine"},
        )
    return _collection


def collection_count() -> int:
    return get_collection().count()


def persist_dir() -> str:
    return str(PERSIST_DIR)


def collection_name() -> str:
    return COLLECTION_NAME
