#!/usr/bin/env bash
# 被 start-dev.sh / start-all.sh source 使用（不要单独 exec）。
# 启动前释放本机 TCP 监听：SIGTERM，仍监听则 SIGKILL。
# 在 .env.local 里设 CAT_RESCUE_DEV_NO_KILL=1 可关闭自动 kill。

kill_tcp_listen_port() {
  local port="$1"
  local label="${2:-TCP ${port}}"

  if [[ -n "${CAT_RESCUE_DEV_NO_KILL:-}" ]]; then
    return 0
  fi
  if ! command -v lsof >/dev/null 2>&1; then
    echo "[kill-port] lsof not found; cannot auto-free ${label}." >&2
    return 0
  fi

  local pids=""
  # lsof exits 1 when nothing matches; with set -o pipefail that would abort the whole script unless we || true.
  pids="$(lsof -nP -tiTCP:"${port}" -sTCP:LISTEN 2>/dev/null | sort -u | tr '\n' ' ')" || true
  pids="${pids//  / }"
  pids="${pids# }"
  pids="${pids% }"
  if [[ -z "${pids}" ]]; then
    return 0
  fi

  echo "[kill-port] ${label}: freeing port ${port} (SIGTERM → PIDs: ${pids})"
  # shellcheck disable=SC2086
  kill ${pids} 2>/dev/null || true
  sleep 1

  pids="$(lsof -nP -tiTCP:"${port}" -sTCP:LISTEN 2>/dev/null | sort -u | tr '\n' ' ')" || true
  pids="${pids//  / }"
  pids="${pids# }"
  pids="${pids% }"
  if [[ -n "${pids}" ]]; then
    echo "[kill-port] ${label}: still listening — SIGKILL → ${pids}"
    # shellcheck disable=SC2086
    kill -9 ${pids} 2>/dev/null || true
    sleep 1
  fi
}
