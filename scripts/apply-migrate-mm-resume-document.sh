#!/usr/bin/env bash
# 在业务库中创建 mm_resume_document（幂等）。从仓库根目录执行。
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

if [[ -f .env ]]; then
  set -a
  # shellcheck disable=SC1091
  source .env
  set +a
fi

DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-3306}"
DB_USER="${DB_USER:-root}"
DB_NAME="${DB_NAME:-MienMieApp}"

MYSQL=(mysql -h"$DB_HOST" -P"$DB_PORT" -u"$DB_USER")
if [[ -n "${DB_PASSWORD:-}" ]]; then
  MYSQL+=(--password="$DB_PASSWORD")
fi

echo "[apply] $DB_HOST:$DB_PORT as $DB_USER (SQL 内含 USE ${DB_NAME})"
"${MYSQL[@]}" < scripts/migrate-mm-resume-document.sql
echo "[apply] done: mm_resume_document"
