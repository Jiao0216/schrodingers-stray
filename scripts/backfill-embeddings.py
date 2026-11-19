#!/usr/bin/env python3
"""重新计算 MySQL 里已存照片的同猫向量，写入 embedding-service 当前的 Chroma 集合。

什么时候用：换了编码器（例如 CLIP -> DINOv2）、换了 embedding_version，或 Chroma 数据丢了
（新机器 / 部署后没挂持久卷）。新集合是空的，旧猫在“找同一只猫”里匹配不到，跑一次即可补齐。

做什么：读取 sightings 和 assessments 表里有 image_bytes 的行，调用 embedding-service 的
POST /embed（默认先裁出猫身），再用 POST /chroma/add 写入。向量 id 和 metadata 与 Spring 后端
写入时一致（sighting_<id>、assessment_<uuid>），/chroma/add 是 upsert，重复运行不会产生重复数据。

用法（在仓库根目录，embedding-service 和 MySQL 都需在运行）：
    set -a && source .env.local && set +a
    embedding-service/.venv/bin/python scripts/backfill-embeddings.py --dry-run   # 只算不写
    embedding-service/.venv/bin/python scripts/backfill-embeddings.py

环境变量：MYSQL_HOST / MYSQL_PORT / MYSQL_DATABASE / MYSQL_USER / MYSQL_PASSWORD（默认值同
application.yml），CAT_RESCUE_EMBEDDING_BASE_URL（默认 http://127.0.0.1:8001），
MYSQL_BIN（mysql 命令行路径，默认在 PATH 或 /usr/local/mysql/bin/mysql 里找）。
只依赖标准库；通过 mysql 命令行读库，密码经 MYSQL_PWD 传入，不出现在命令行参数里。
"""
from __future__ import annotations

import argparse
import json
import os
import shutil
import subprocess
import sys
import urllib.error
import urllib.request
import uuid

# Same limit as SightingDeduplicationService.MAX_STORED_IMAGE_URL_CHARS.
MAX_STORED_IMAGE_URL_CHARS = 240


def env(name: str, default: str) -> str:
    value = os.environ.get(name, "")
    return value if value.strip() else default


EMBEDDING_BASE_URL = env("CAT_RESCUE_EMBEDDING_BASE_URL", "http://127.0.0.1:8001").rstrip("/")


def mysql_bin() -> str:
    for candidate in (os.environ.get("MYSQL_BIN"), shutil.which("mysql"), "/usr/local/mysql/bin/mysql",
                      "/opt/homebrew/bin/mysql"):
        if candidate and os.path.exists(candidate):
            return candidate
    sys.exit("mysql command-line client not found; set MYSQL_BIN")


def query(sql: str) -> list[list[str]]:
    proc_env = dict(os.environ, MYSQL_PWD=os.environ.get("MYSQL_PASSWORD", ""))
    cmd = [
        mysql_bin(),
        "-h", env("MYSQL_HOST", "localhost"),
        "-P", env("MYSQL_PORT", "3306"),
        "-u", env("MYSQL_USER", "root"),
        env("MYSQL_DATABASE", "cat_rescue"),
        "-N", "-B", "--max-allowed-packet=512M",
        "-e", sql,
    ]
    proc = subprocess.run(cmd, env=proc_env, capture_output=True, text=True)
    if proc.returncode != 0:
        sys.exit(f"MySQL query failed: {proc.stderr.strip()}")
    return [line.split("\t") for line in proc.stdout.splitlines() if line]


def value(v: str) -> str | None:
    return None if v == "NULL" else v


def java_instant(ts: str) -> str:
    """MySQL DATETIME(6) as Hibernate stores an Instant (UTC) -> Java Instant.toString()."""
    date, time = ts.split(" ")
    whole, _, frac = time.partition(".")
    frac = frac.ljust(9, "0")[:9]
    if frac == "000000000":
        return f"{date}T{whole}Z"
    if frac.endswith("000000"):
        return f"{date}T{whole}.{frac[:3]}Z"
    if frac.endswith("000"):
        return f"{date}T{whole}.{frac[:6]}Z"
    return f"{date}T{whole}.{frac}Z"


def post(path: str, data: bytes, content_type: str, timeout: int) -> dict:
    req = urllib.request.Request(f"{EMBEDDING_BASE_URL}{path}", data=data, headers={"Content-Type": content_type})
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            return json.load(resp)
    except urllib.error.HTTPError as exc:
        raise RuntimeError(f"{path} -> HTTP {exc.code}: {exc.read().decode(errors='replace')[:300]}") from exc


def embed(raw: bytes, content_type: str | None) -> dict:
    boundary = uuid.uuid4().hex
    head = (
        f"--{boundary}\r\n"
        'Content-Disposition: form-data; name="file"; filename="photo.jpg"\r\n'
        f"Content-Type: {content_type or 'image/jpeg'}\r\n\r\n"
    ).encode()
    body = head + raw + f"\r\n--{boundary}--\r\n".encode()
    return post("/embed", body, f"multipart/form-data; boundary={boundary}", timeout=300)


def safe_image_reference(image_url: str | None) -> str:
    v = image_url or ""
    return v if len(v) <= MAX_STORED_IMAGE_URL_CHARS else ""


def sighting_jobs():
    rows = query(
        "SELECT id, cat_id, occurred_at, latitude, longitude, image_url, image_content_type, HEX(image_bytes) "
        "FROM sightings WHERE image_bytes IS NOT NULL ORDER BY id"
    )
    for sid, cat_id, occurred, lat, lng, image_url, ctype, hexbytes in rows:
        meta = {
            "id": f"sighting_{sid}",
            "sighting_id": int(sid),
            "cat_id": int(cat_id) if value(cat_id) else None,
            "upload_time": java_instant(occurred),
            "location_approx": f"{round(float(lat), 2)},{round(float(lng), 2)}",
            "image_url": safe_image_reference(value(image_url)),
        }
        yield meta, bytes.fromhex(hexbytes), value(ctype)


def assessment_jobs():
    rows = query(
        "SELECT HEX(id), created_at, tracking_sighting_id, image_stored+0, address_text, latitude, longitude, "
        "content_type, HEX(image_bytes) FROM assessments WHERE image_bytes IS NOT NULL ORDER BY created_at"
    )
    for hid, created, tsid, stored, addr, lat, lng, ctype, hexbytes in rows:
        aid = str(uuid.UUID(hid))
        addr, lat, lng = value(addr), value(lat), value(lng)
        if addr and addr.strip():
            loc = addr.strip()
        elif lat and lng:
            loc = f"{float(lat):.5f}, {float(lng):.5f}"
        else:
            loc = "Unknown"
        meta = {
            "id": f"assessment_{aid}",
            "sighting_id": int(tsid) if value(tsid) else 0,
            "cat_id": None,
            "upload_time": java_instant(created),
            "location_approx": loc,
            "image_url": f"/api/v1/assessments/{aid}/image" if value(stored) not in (None, "0") else "",
        }
        yield meta, bytes.fromhex(hexbytes), value(ctype)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    parser.add_argument("--dry-run", action="store_true", help="compute embeddings but do not write to Chroma")
    args = parser.parse_args()

    with urllib.request.urlopen(f"{EMBEDDING_BASE_URL}/health", timeout=10) as resp:
        health = json.load(resp)
    if not health.get("ready"):
        sys.exit(f"embedding-service not ready: {health}")
    print(f"encoder={health.get('encoder')} version={health.get('embedding_version')} "
          f"collection={health.get('chroma', {}).get('collection')}")

    written = failed = 0
    for jobs in (sighting_jobs(), assessment_jobs()):
        for meta, raw, ctype in jobs:
            try:
                res = embed(raw, ctype)
            except Exception as exc:  # keep going; report at the end
                failed += 1
                print(f"{meta['id']}: embed failed: {exc}")
                continue
            crop = res.get("crop") or {}
            print(f"{meta['id']}: cat={meta['cat_id']} dims={res['dimensions']} "
                  f"crop={'yes' if crop.get('applied') else 'no'} score={crop.get('score')}")
            if args.dry_run:
                continue
            payload = dict(meta, embedding=res["embedding"], embedding_version=res["embedding_version"])
            post("/chroma/add", json.dumps(payload).encode(), "application/json", timeout=60)
            written += 1

    with urllib.request.urlopen(f"{EMBEDDING_BASE_URL}/chroma/stats", timeout=10) as resp:
        stats = json.load(resp)
    mode = "dry run, nothing written" if args.dry_run else f"written={written}"
    print(f"done: {mode}, failed={failed}; collection stats: {stats}")
    if failed:
        sys.exit(1)


if __name__ == "__main__":
    main()
