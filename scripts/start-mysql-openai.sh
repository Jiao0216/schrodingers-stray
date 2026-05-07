#!/usr/bin/env bash
# Start Cat Rescue API with MySQL + OpenAI (no H2 "local" profile).
#
# Usage:
#   1) Copy .env.example -> .env.local , fill MYSQL_PASSWORD and OPENAI_API_KEY , then:
#        ./scripts/start-mysql-openai.sh
#   2) Or export vars yourself, then run this script:
#        export MYSQL_PASSWORD='...' OPENAI_API_KEY='sk-...'
#        ./scripts/start-mysql-openai.sh
#
# Ensure MySQL is running and database exists (ddl-auto=update will create tables).

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
ENV_FILE="${CAT_RESCUE_ENV_FILE:-${PROJECT_ROOT}/.env.local}"

if [[ -f "${ENV_FILE}" ]]; then
  echo "[start-mysql-openai] Loading ${ENV_FILE}"
  set -a
  # shellcheck disable=SC1090
  source "${ENV_FILE}"
  set +a
fi

# Defaults (override in .env.local or export before running)
export CAT_RESCUE_HTTP_PORT="${CAT_RESCUE_HTTP_PORT:-8090}"
export MYSQL_HOST="${MYSQL_HOST:-127.0.0.1}"
export MYSQL_PORT="${MYSQL_PORT:-3306}"
export MYSQL_DATABASE="${MYSQL_DATABASE:-cat_rescue}"
export MYSQL_USER="${MYSQL_USER:-root}"
export MULTIMODAL_PROVIDER="${MULTIMODAL_PROVIDER:-openai}"

if [[ -z "${MYSQL_PASSWORD:-}" ]]; then
  echo "[start-mysql-openai] MYSQL_PASSWORD is empty. Set it in ${ENV_FILE} or: export MYSQL_PASSWORD='...'"
  exit 1
fi

if [[ -z "${OPENAI_API_KEY:-}" ]] && [[ -z "${CAT_RESCUE_OPENAI_API_KEY:-}" ]]; then
  echo "[start-mysql-openai] OPENAI_API_KEY (or CAT_RESCUE_OPENAI_API_KEY) is empty. Real vision calls will use Stub."
  echo "[start-mysql-openai] Set OPENAI_API_KEY in ${ENV_FILE} or export OPENAI_API_KEY='sk-...'"
  exit 1
fi

LISTEN_PORT="${CAT_RESCUE_HTTP_PORT}"
if command -v lsof >/dev/null 2>&1; then
  if lsof -nP -iTCP:"${LISTEN_PORT}" -sTCP:LISTEN >/dev/null 2>&1; then
    echo "[start-mysql-openai] Port ${LISTEN_PORT} is already in use. Stop the other process or: export CAT_RESCUE_HTTP_PORT=8091"
    exit 1
  fi
fi

echo "[start-mysql-openai] Starting with MySQL ${MYSQL_HOST}:${MYSQL_PORT}/${MYSQL_DATABASE}, HTTP port ${LISTEN_PORT}"
cd "${PROJECT_ROOT}"
exec mvn spring-boot:run
