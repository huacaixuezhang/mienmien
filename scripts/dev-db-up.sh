#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"

if ! command -v docker >/dev/null 2>&1; then
  echo "未检测到 docker，跳过容器数据库启动（请本机安装 MySQL 并创建库 MienMieApp）。"
  exit 0
fi

compose_up() {
  if docker compose version >/dev/null 2>&1; then
    if (cd "$ROOT_DIR" && docker compose up -d mysql); then
      return 0
    fi
  fi
  if command -v docker-compose >/dev/null 2>&1; then
    if (cd "$ROOT_DIR" && docker-compose up -d mysql); then
      return 0
    fi
  fi
  return 1
}

compose_exec_mysqladmin() {
  local pass="$1"
  if docker compose version >/dev/null 2>&1; then
    docker compose -f "$ROOT_DIR/docker-compose.yml" exec -T mysql mysqladmin ping -h 127.0.0.1 -uroot -p"$pass" --silent
    return $?
  fi
  if command -v docker-compose >/dev/null 2>&1; then
    docker-compose -f "$ROOT_DIR/docker-compose.yml" exec -T mysql mysqladmin ping -h 127.0.0.1 -uroot -p"$pass" --silent
    return $?
  fi
  return 1
}

cd "$ROOT_DIR"

export DB_NAME="${DB_NAME:-MienMieApp}"
export DB_PASSWORD="${DB_PASSWORD:-root}"

if ! compose_up; then
  echo "未检测到可用的 docker compose 插件或 docker-compose 命令，跳过容器数据库启动。"
  echo "可选方案：安装 Docker Desktop（含 Compose 插件），或单独安装 docker-compose v1，或使用本机 MySQL + bash scripts/dev-seed.sh。"
  exit 0
fi

echo "等待数据库就绪..."
for i in $(seq 1 90); do
  if compose_exec_mysqladmin "$DB_PASSWORD" >/dev/null 2>&1; then
    echo "数据库已就绪。"
    exit 0
  fi
  sleep 1
done

echo "超时：MySQL 未在 90s 内就绪。"
exit 1
