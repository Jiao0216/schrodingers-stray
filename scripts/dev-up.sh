#!/usr/bin/env bash
# 本地一键启动入口：等价于 scripts/start-all.sh
#   等待 MySQL →（默认）后台启动 cat-agent:8000 → 前台 Spring Boot（见 start-dev.sh）
#
# 用法：
#   ./scripts/dev-up.sh
#   ./scripts/dev-up.sh --skip-agent
#   ./scripts/dev-up.sh --mysql-wait-seconds 120
#
# 环境：复制 .env.example → .env.local 并填写；可选 export CAT_RESCUE_ENV_FILE=其他路径
# 停止 cat-agent：./scripts/stop-all.sh ；Java 在终端 Ctrl+C

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
exec "${SCRIPT_DIR}/start-all.sh" "$@"
