#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
if [[ -f "$ROOT_DIR/.dev-pids" ]]; then
  while read -r pid; do
    kill "$pid" >/dev/null 2>&1 || true
  done < "$ROOT_DIR/.dev-pids"
  rm -f "$ROOT_DIR/.dev-pids"
fi

echo "已停止 dev-up 启动的进程"

if [[ "${DEV_STOP_DB:-0}" == "1" ]] && command -v docker >/dev/null 2>&1; then
  cd "$ROOT_DIR"
  echo "DEV_STOP_DB=1：停止 MySQL 容器..."
  if docker compose version >/dev/null 2>&1; then
    docker compose stop mysql >/dev/null 2>&1 || true
  elif command -v docker-compose >/dev/null 2>&1; then
    docker-compose stop mysql >/dev/null 2>&1 || true
  fi
fi
