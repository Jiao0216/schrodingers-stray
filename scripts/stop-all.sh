#!/usr/bin/env bash
# 停止由 start-all.sh 写入 logs/cat-agent.pid 的 cat-agent 进程（Spring 需自行停止 mvn / IDE）
#
# 用法：./scripts/stop-all.sh

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
PID_FILE="${PROJECT_ROOT}/logs/cat-agent.pid"

if [[ -f "${PID_FILE}" ]]; then
  pid="$(<"${PID_FILE}")"
  if [[ -n "${pid}" ]] && kill -0 "${pid}" 2>/dev/null; then
    echo "[stop-all] Sending SIGTERM to cat-agent PID ${pid} …"
    kill "${pid}" 2>/dev/null || true
    sleep 1
    if kill -0 "${pid}" 2>/dev/null; then
      echo "[stop-all] Still running — SIGKILL …"
      kill -9 "${pid}" 2>/dev/null || true
    fi
  else
    echo "[stop-all] PID file stale or process already gone (${pid:-empty})."
  fi
  rm -f "${PID_FILE}"
else
  echo "[stop-all] No ${PID_FILE} — nothing to stop for cat-agent."
fi

echo "[stop-all] Spring Boot: stop your terminal running mvn spring-boot:run, or: pkill -f 'spring-boot:run' (use with care)."
