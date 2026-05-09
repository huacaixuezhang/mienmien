#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
REPORT_FILE="$ROOT_DIR/.dev-last-report.md"

if [[ ! -f "$REPORT_FILE" ]]; then
  echo "未找到最近报告：$REPORT_FILE"
  echo "请先执行：bash scripts/dev-all-jdk21.sh"
  exit 1
fi

echo "[report] $REPORT_FILE"
awk '{print}' "$REPORT_FILE"
