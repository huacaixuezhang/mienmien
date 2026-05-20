#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
SQL_FILE="$ROOT_DIR/scripts/seed-mienmien.sql"

DB_NAME="${DB_NAME:-MienMieApp}"
DB_HOST="${DB_HOST:-127.0.0.1}"
DB_PORT="${DB_PORT:-3306}"
DB_USER="${DB_USER:-root}"
DOCKER_CONTAINER="${DOCKER_MYSQL_CONTAINER:-mienmien-mysql}"

if [[ ! -f "$SQL_FILE" ]]; then
  echo "缺少种子 SQL 文件: $SQL_FILE"
  exit 1
fi

if ! command -v mysql >/dev/null 2>&1; then
  if command -v docker >/dev/null 2>&1; then
    if ! docker ps --format '{{.Names}}' 2>/dev/null | grep -qx "$DOCKER_CONTAINER"; then
      echo "[dev-seed] 未检测到 mysql 客户端，且容器未运行，尝试 bash scripts/dev-db-up.sh ..."
      bash "$ROOT_DIR/scripts/dev-db-up.sh" || true
    fi
  fi
fi

run_mysql_local() {
  local -a args=(-h"$DB_HOST" -P"$DB_PORT" -u"$DB_USER")
  if [[ -n "${DB_PASSWORD:-}" ]]; then
    args+=(-p"$DB_PASSWORD")
  fi
  mysql "${args[@]}" < "$SQL_FILE"
}

run_mysql_docker() {
  local pass="${DB_PASSWORD:-root}"
  docker exec -i "$DOCKER_CONTAINER" mysql -uroot -p"$pass" < "$SQL_FILE"
}

if command -v mysql >/dev/null 2>&1; then
  run_mysql_local
elif docker ps --format '{{.Names}}' 2>/dev/null | grep -qx "$DOCKER_CONTAINER"; then
  run_mysql_docker
else
  echo "无法初始化数据库：请安装 mysql 客户端并启动本机 MySQL，或先执行 bash scripts/dev-db-up.sh 启动容器 $DOCKER_CONTAINER。"
  echo "本机需已创建库 MienMieApp（种子脚本会 CREATE DATABASE IF NOT EXISTS）。"
  exit 1
fi

echo "数据库初始化完成: $DB_NAME"
