#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
ENV_FILE="${CAT_RESCUE_ENV_FILE:-${PROJECT_ROOT}/.env.local}"

if [[ -f "${ENV_FILE}" ]]; then
  echo "[start-tunnel] Loading env from ${ENV_FILE}"
  set -a
  # shellcheck disable=SC1090
  source "${ENV_FILE}"
  set +a
fi

APP_PORT="${APP_PORT:-8090}"
TARGET_URL="${TUNNEL_TARGET_URL:-http://localhost:${APP_PORT}}"

if ! command -v cloudflared >/dev/null 2>&1; then
  echo "[start-tunnel] cloudflared not found. Install first: brew install cloudflared"
  exit 1
fi

if ! lsof -nP -iTCP:"${APP_PORT}" -sTCP:LISTEN >/dev/null 2>&1; then
  echo "[start-tunnel] No local service is listening on port ${APP_PORT}."
  echo "[start-tunnel] Start backend first (e.g. ./scripts/start-dev.sh), then retry."
  exit 1
fi

echo "[start-tunnel] Starting Cloudflare quick tunnel -> ${TARGET_URL}"
echo "[start-tunnel] Keep this terminal open. Press Ctrl+C to stop."
exec cloudflared tunnel --url "${TARGET_URL}"
