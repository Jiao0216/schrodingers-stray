#!/usr/bin/env bash
# 一键本地联调：等待 MySQL 端口 →（可选）后台启动 cat-agent → 前台启动 Spring Boot（同 start-dev.sh）
# 默认会先尝试结束占用 8000 / CAT_RESCUE_HTTP_PORT 的监听进程；设 CAT_RESCUE_DEV_NO_KILL=1 可关闭。
#
# 用法：
#   ./scripts/start-all.sh
#   ./scripts/start-all.sh --skip-agent          # 只起 Java，不起 cat-agent
#   ./scripts/start-all.sh --mysql-wait-seconds 120
#
# 依赖：
#   - 已配置 .env.local（与 scripts/start-dev.sh 相同，含 MYSQL_*、MULTIMODAL_PROVIDER 等）
#   - MySQL 已在本机或 Docker 监听 MYSQL_HOST:MYSQL_PORT（默认 127.0.0.1:3306）
#   - cat-agent：自动创建 cat-agent/.venv 并 pip install -r requirements.txt（兼容 Homebrew PEP 668）
#
# 日志：logs/cat-agent.log ；PID：logs/cat-agent.pid（目录已 .gitignore）

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
LOG_DIR="${PROJECT_ROOT}/logs"
ENV_FILE="${CAT_RESCUE_ENV_FILE:-${PROJECT_ROOT}/.env.local}"

SKIP_AGENT=0
MYSQL_WAIT_SECONDS=90

usage() {
  cat <<'EOF'
Usage: scripts/start-all.sh [options]

  等待 MySQL (TCP)，可选启动 cat-agent (8000)，然后 exec scripts/start-dev.sh（Spring Boot）。

Options:
  --skip-agent                 不启动 cat-agent
  --mysql-wait-seconds <n>     等待 MySQL 端口的最长时间（默认 90）
  -h, --help                   显示本说明

示例：
  ./scripts/start-all.sh
  ./scripts/start-all.sh --skip-agent
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --skip-agent) SKIP_AGENT=1 ;;
    --mysql-wait-seconds)
      shift
      MYSQL_WAIT_SECONDS="${1:?--mysql-wait-seconds requires a number}"
      ;;
    -h|--help) usage; exit 0 ;;
    *)
      echo "Unknown option: $1" >&2
      usage
      exit 1
      ;;
  esac
  shift
done

mkdir -p "${LOG_DIR}"

if [[ -f "${ENV_FILE}" ]]; then
  echo "[start-all] Loading ${ENV_FILE}"
  set -a
  # shellcheck disable=SC1090
  source "${ENV_FILE}"
  set +a
else
  echo "[start-all] Missing ${ENV_FILE} — copy .env.example -> .env.local first." >&2
  exit 1
fi

export MYSQL_HOST="${MYSQL_HOST:-127.0.0.1}"
export MYSQL_PORT="${MYSQL_PORT:-3306}"

# shellcheck disable=SC1091
source "${SCRIPT_DIR}/lib-dev-ports.sh"

port_listen() {
  local port="$1"
  if command -v lsof >/dev/null 2>&1; then
    lsof -nP -iTCP:"${port}" -sTCP:LISTEN >/dev/null 2>&1
  else
    return 1
  fi
}

wait_for_mysql() {
  local host="$1" port="$2"
  local start
  start="$(date +%s)"
  echo "[start-all] Waiting for TCP ${host}:${port} (up to ${MYSQL_WAIT_SECONDS}s)…"
  while true; do
    if bash -c "echo >/dev/tcp/${host}/${port}" &>/dev/null; then
      echo "[start-all] Port ${host}:${port} is reachable."
      return 0
    fi
    local now
    now="$(date +%s)"
    if (( now - start >= MYSQL_WAIT_SECONDS )); then
      echo "[start-all] ERROR: cannot connect to ${host}:${port} within ${MYSQL_WAIT_SECONDS}s." >&2
      echo "[start-all] Start MySQL first, e.g. brew services start mysql, or Docker mapping 3306." >&2
      exit 1
    fi
    sleep 1
  done
}

# 使用 cat-agent/.venv + requirements.txt，避免 Homebrew Python 的 PEP 668（禁止全局 pip install）。
ensure_cat_agent_venv() {
  local req="${PROJECT_ROOT}/cat-agent/requirements.txt"
  local venv="${PROJECT_ROOT}/cat-agent/.venv"

  if [[ ! -f "${req}" ]]; then
    echo "[start-all] WARN: missing ${req}; skip cat-agent." >&2
    return 1
  fi
  if ! command -v python3 >/dev/null 2>&1; then
    echo "[start-all] WARN: python3 not on PATH; skip cat-agent." >&2
    return 1
  fi
  if [[ ! -x "${venv}/bin/python" ]]; then
    echo "[start-all] Creating cat-agent/.venv …"
    python3 -m venv "${venv}" || {
      echo "[start-all] ERROR: python3 -m venv failed." >&2
      return 1
    }
  fi
  if ! "${venv}/bin/python" -c "import uvicorn, fastapi, httpx, langchain_openai, langgraph" 2>/dev/null; then
    echo "[start-all] pip install -r cat-agent/requirements.txt (首次可能较慢) …"
    "${venv}/bin/pip" install -U pip setuptools wheel >/dev/null 2>&1 || true
    "${venv}/bin/pip" install -r "${req}" || {
      echo "[start-all] ERROR: pip install in cat-agent/.venv failed." >&2
      return 1
    }
  fi
  return 0
}

start_cat_agent() {
  kill_tcp_listen_port 8000 "cat-agent (8000)"
  rm -f "${LOG_DIR}/cat-agent.pid"
  if port_listen 8000; then
    echo "[start-all] Port 8000 still in use after kill — skip starting cat-agent." >&2
    return 0
  fi
  if ! ensure_cat_agent_venv; then
    return 0
  fi
  local py="${PROJECT_ROOT}/cat-agent/.venv/bin/python"
  if [[ ! -x "${py}" ]]; then
    echo "[start-all] WARN: missing ${py}; skip cat-agent." >&2
    return 0
  fi
  echo "[start-all] Starting cat-agent (uvicorn) → 127.0.0.1:8000 …"
  cd "${PROJECT_ROOT}/cat-agent" || {
    echo "[start-all] ERROR: cannot cd to ${PROJECT_ROOT}/cat-agent" >&2
    exit 1
  }
  # shellcheck disable=SC2094
  nohup "${py}" -m uvicorn api:app --host 127.0.0.1 --port 8000 >>"${LOG_DIR}/cat-agent.log" 2>&1 &
  echo $! >"${LOG_DIR}/cat-agent.pid"
  cd "${PROJECT_ROOT}" || {
    echo "[start-all] ERROR: cannot cd back to ${PROJECT_ROOT}" >&2
    exit 1
  }
  sleep 1
  if port_listen 8000; then
    echo "[start-all] cat-agent OK — PID $(<"${LOG_DIR}/cat-agent.pid"), log: ${LOG_DIR}/cat-agent.log"
  else
    echo "[start-all] WARN: cat-agent may have failed; see ${LOG_DIR}/cat-agent.log" >&2
  fi
}

wait_for_mysql "${MYSQL_HOST}" "${MYSQL_PORT}"

if [[ "${SKIP_AGENT}" != "1" ]]; then
  start_cat_agent
fi

echo "[start-all] Starting Spring Boot via scripts/start-dev.sh …"
exec "${PROJECT_ROOT}/scripts/start-dev.sh"
