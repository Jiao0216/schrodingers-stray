#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
ENV_FILE="${CAT_RESCUE_ENV_FILE:-${PROJECT_ROOT}/.env.local}"

if [[ -f "${ENV_FILE}" ]]; then
  echo "[start-dev] Loading env from ${ENV_FILE}"
  set -a
  # shellcheck disable=SC1090
  source "${ENV_FILE}"
  set +a
else
  echo "[start-dev] Missing ${ENV_FILE}"
  echo "[start-dev] Copy .env.example -> .env.local and fill your secrets."
  exit 1
fi

required_vars=(
  MYSQL_HOST
  MYSQL_PORT
  MYSQL_USER
  MYSQL_PASSWORD
  MULTIMODAL_PROVIDER
)

for v in "${required_vars[@]}"; do
  if [[ -z "${!v:-}" ]]; then
    echo "[start-dev] Required env var is empty: ${v}"
    exit 1
  fi
done

# Spring config uses MYSQL_DATABASE; keep backward compatibility with MYSQL_DB.
if [[ -z "${MYSQL_DATABASE:-}" ]] && [[ -n "${MYSQL_DB:-}" ]]; then
  export MYSQL_DATABASE="${MYSQL_DB}"
fi

if [[ -z "${MYSQL_DATABASE:-}" ]]; then
  echo "[start-dev] Required env var is empty: MYSQL_DATABASE (or MYSQL_DB)"
  exit 1
fi

if [[ "${MULTIMODAL_PROVIDER}" == "openai" ]] && [[ -z "${OPENAI_API_KEY:-}" ]] && [[ -z "${CAT_RESCUE_OPENAI_API_KEY:-}" ]]; then
  echo "[start-dev] MULTIMODAL_PROVIDER=openai but OPENAI_API_KEY (or CAT_RESCUE_OPENAI_API_KEY) is empty."
  exit 1
fi

if [[ "${MULTIMODAL_PROVIDER}" == "gemini" ]] && [[ -z "${GEMINI_API_KEY:-}" ]]; then
  echo "[start-dev] MULTIMODAL_PROVIDER=gemini but GEMINI_API_KEY is empty."
  exit 1
fi

LISTEN_PORT="${CAT_RESCUE_HTTP_PORT:-8090}"
if lsof -nP -iTCP:"${LISTEN_PORT}" -sTCP:LISTEN >/dev/null 2>&1; then
  echo "[start-dev] Port ${LISTEN_PORT} is already in use (CAT_RESCUE_HTTP_PORT). Stop the other process or pick another port."
  echo "[start-dev] Example: export CAT_RESCUE_HTTP_PORT=8091"
  exit 1
fi

echo "[start-dev] Starting Spring Boot..."
cd "${PROJECT_ROOT}"
exec mvn spring-boot:run
