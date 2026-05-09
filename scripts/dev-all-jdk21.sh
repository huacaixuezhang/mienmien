#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
START_TS="$(date +%s)"
REPORT_FILE="$ROOT_DIR/.dev-last-report.md"
STEP_UP_SECONDS=0
STEP_HEALTH_SECONDS=0
STEP_CHECK_SECONDS=0
STEP_WARN_SECONDS="${DEV_ALL_WARN_STEP_SECONDS:-120}"
RUN_BACKEND=1
RUN_WEB=1
if [[ "${DEV_ALL_SKIP_BACKEND:-0}" == "1" ]]; then
  RUN_BACKEND=0
fi
if [[ "${DEV_ALL_SKIP_WEB:-0}" == "1" ]]; then
  RUN_WEB=0
fi
if [[ "$RUN_BACKEND" -eq 0 && "$RUN_WEB" -eq 0 ]]; then
  echo "[all] DEV_ALL_SKIP_BACKEND=1 且 DEV_ALL_SKIP_WEB=1，未选择任何执行目标。"
  exit 1
fi

load_env_file() {
  local env_file="$ROOT_DIR/.env"
  if [[ -f "$env_file" ]]; then
    echo "[all] 检测到 .env，自动加载环境变量"
    set -a
    # shellcheck disable=SC1090
    source "$env_file"
    set +a
  fi
}

cleanup_ports() {
  local ports=()
  if [[ "$RUN_BACKEND" -eq 1 ]]; then
    ports+=("8080" "8081")
  fi
  if [[ "$RUN_WEB" -eq 1 ]]; then
    ports+=("5173")
  fi
  for p in "${ports[@]}"; do
    local ids
    ids="$(lsof -t -iTCP:"$p" -sTCP:LISTEN 2>/dev/null || true)"
    if [[ -n "$ids" ]]; then
      echo "[all] 检测到端口 ${p} 被占用，尝试释放: ${ids}"
      kill $ids >/dev/null 2>&1 || true
    fi
  done
}

wait_health() {
  local name="$1"
  local url="$2"
  local max_retry="${3:-20}"
  local i=1
  while [[ "$i" -le "$max_retry" ]]; do
    if curl -fsS "$url" >/dev/null 2>&1; then
      echo "[all] ${name} 健康检查通过（第 ${i} 次）"
      return 0
    fi
    sleep 1
    i=$((i + 1))
  done
  echo "[all] ${name} 健康检查超时：$url"
  return 1
}

warn_slow_step() {
  local name="$1"
  local seconds="$2"
  if [[ "$seconds" -gt "$STEP_WARN_SECONDS" ]]; then
    echo "[all][warn] 步骤 ${name} 耗时 ${seconds}s，超过阈值 ${STEP_WARN_SECONDS}s"
  fi
}

archive_failure_artifacts() {
  local ts
  ts="$(date '+%Y%m%d-%H%M%S')"
  local archive_dir="$ROOT_DIR/.dev-archives/$ts"
  mkdir -p "$archive_dir"
  for f in ".business.log" ".consumer.log" ".web.log" ".dev-pids" ".dev-last-report.md"; do
    if [[ -f "$ROOT_DIR/$f" ]]; then
      cp "$ROOT_DIR/$f" "$archive_dir/$f" || true
    fi
  done
  echo "[all] 失败归档目录: $archive_dir"
}

write_report() {
  local status="$1"
  local code="$2"
  local end_ts
  end_ts="$(date +%s)"
  {
    echo "# Dev All JDK21 Report"
    echo
    echo "- status: ${status}"
    echo "- exit_code: ${code}"
    echo "- started_at: ${START_TS}"
    echo "- ended_at: ${end_ts}"
    echo "- elapsed_seconds: $(( end_ts - START_TS ))"
    echo "- step_up_seconds: ${STEP_UP_SECONDS}"
    echo "- step_health_seconds: ${STEP_HEALTH_SECONDS}"
    echo "- step_check_seconds: ${STEP_CHECK_SECONDS}"
    echo "- step_warn_seconds: ${STEP_WARN_SECONDS}"
    echo "- health_retry: ${DEV_ALL_HEALTH_RETRY:-25}"
    echo "- auto_down_on_fail: ${DEV_ALL_AUTO_DOWN_ON_FAIL:-0}"
    echo "- skip_backend: ${DEV_ALL_SKIP_BACKEND:-0}"
    echo "- skip_web: ${DEV_ALL_SKIP_WEB:-0}"
    echo "- skip_check: ${DEV_ALL_SKIP_CHECK:-0}"
    echo
    echo "## Quick Commands"
    echo
    echo "- status: \`bash scripts/dev-status.sh\`"
    echo "- retry: \`DEV_ALL_AUTO_DOWN_ON_FAIL=1 bash scripts/dev-all-jdk21.sh\`"
  } > "$REPORT_FILE"
}

on_fail() {
  local code="$?"
  local elapsed="$(( $(date +%s) - START_TS ))"
  echo "[all] 失败，exit_code=${code}"
  echo "[all] 已运行 ${elapsed}s"
  echo "[all] 诊断信息："
  bash "$ROOT_DIR/scripts/dev-status.sh" || true
  echo "[all] 最近日志片段："
  for f in "$ROOT_DIR/.business.log" "$ROOT_DIR/.consumer.log" "$ROOT_DIR/.web.log"; do
    if [[ -f "$f" ]]; then
      echo "--- ${f##*/} ---"
      awk 'NF{a[i++%40]=$0} END{for(j=i;j<i+40;j++) if(a[j%40]!="") print a[j%40]}' "$f" || true
    fi
  done
  if [[ "${DEV_ALL_AUTO_DOWN_ON_FAIL:-0}" == "1" ]]; then
    echo "[all] DEV_ALL_AUTO_DOWN_ON_FAIL=1，自动执行 dev-down..."
    bash "$ROOT_DIR/scripts/dev-down.sh" || true
  fi
  echo "[all] 建议下一步："
  echo "  1) bash scripts/dev-status.sh"
  echo "  2) 查看 .business.log / .consumer.log / .web.log"
  echo "  3) 如需重试：DEV_ALL_AUTO_DOWN_ON_FAIL=1 bash scripts/dev-all-jdk21.sh"
  write_report "failed" "$code"
  archive_failure_artifacts
  echo "[all] 报告已写入: $REPORT_FILE"
  exit "$code"
}

trap on_fail ERR

load_env_file

echo "[all] 预清理端口..."
cleanup_ports

echo "[all] 启动（JDK21）..."
step_start="$(date +%s)"
DEV_UP_SKIP_BACKEND="$((1 - RUN_BACKEND))" DEV_UP_SKIP_WEB="$((1 - RUN_WEB))" bash "$ROOT_DIR/scripts/dev-up-jdk21.sh"
STEP_UP_SECONDS="$(( $(date +%s) - step_start ))"
echo "[all] 启动耗时 ${STEP_UP_SECONDS}s"
warn_slow_step "startup" "$STEP_UP_SECONDS"

if [[ "$RUN_BACKEND" -eq 1 ]]; then
  echo "[all] 等待服务健康..."
  step_start="$(date +%s)"
  wait_health "business" "http://localhost:8080/actuator/health" "${DEV_ALL_HEALTH_RETRY:-25}"
  wait_health "consumer" "http://localhost:8081/actuator/health" "${DEV_ALL_HEALTH_RETRY:-25}"
  STEP_HEALTH_SECONDS="$(( $(date +%s) - step_start ))"
  echo "[all] 健康等待耗时 ${STEP_HEALTH_SECONDS}s"
  warn_slow_step "health" "$STEP_HEALTH_SECONDS"
else
  echo "[all] 跳过健康检查（DEV_ALL_SKIP_BACKEND=1）"
fi

if [[ "${DEV_ALL_SKIP_CHECK:-0}" == "1" ]]; then
  echo "[all] 跳过验收（DEV_ALL_SKIP_CHECK=1）"
else
  if [[ "$RUN_BACKEND" -eq 1 ]]; then
    echo "[all] 验收（JDK21）..."
    step_start="$(date +%s)"
    bash "$ROOT_DIR/scripts/dev-check-jdk21.sh"
    STEP_CHECK_SECONDS="$(( $(date +%s) - step_start ))"
    echo "[all] 验收耗时 ${STEP_CHECK_SECONDS}s"
    warn_slow_step "check" "$STEP_CHECK_SECONDS"
  else
    echo "[all] 跳过验收（DEV_ALL_SKIP_BACKEND=1）"
  fi
fi

echo "[all] 运行状态摘要："
bash "$ROOT_DIR/scripts/dev-status.sh" || true

write_report "success" "0"
echo "[all] 报告已写入: $REPORT_FILE"
echo "[all] 全量完成，总耗时 $(( $(date +%s) - START_TS ))s"
