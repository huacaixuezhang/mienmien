#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"

# 与 dev-all 一致：若存在仓库根目录 `.env`，在跑 seed / Java 前导出 DB_* 等变量（避免 mysql 使用空密码导致 1045）
load_env_file() {
  local env_file="$ROOT_DIR/.env"
  if [[ -f "$env_file" ]]; then
    echo "[env] 加载 $env_file"
    set -a
    # shellcheck disable=SC1090
    source "$env_file"
    set +a
  fi
}
load_env_file

RUN_BACKEND=1
RUN_WEB=1

if [[ "${DEV_UP_SKIP_BACKEND:-0}" == "1" ]]; then
  RUN_BACKEND=0
fi
if [[ "${DEV_UP_SKIP_WEB:-0}" == "1" ]]; then
  RUN_WEB=0
fi
if [[ "$RUN_BACKEND" -eq 0 && "$RUN_WEB" -eq 0 ]]; then
  echo "DEV_UP_SKIP_BACKEND=1 且 DEV_UP_SKIP_WEB=1，未选择任何启动目标。"
  exit 1
fi

if command -v /usr/libexec/java_home >/dev/null 2>&1; then
  JAVA21_HOME="$(/usr/libexec/java_home -v 21 2>/dev/null || true)"
  if [[ -n "${JAVA21_HOME}" ]]; then
    export JAVA_HOME="${JAVA21_HOME}"
    export PATH="${JAVA_HOME}/bin:${PATH}"
  fi
fi

echo "[0/5] 依赖检查..."
if [[ "$RUN_BACKEND" -eq 1 ]]; then
  bash "$ROOT_DIR/scripts/dev-precheck.sh"
else
  for cmd in node npm; do
    if ! command -v "$cmd" >/dev/null 2>&1; then
      echo "缺少命令: $cmd"
      exit 1
    fi
  done
  echo "前端依赖检查通过。"
fi

if [[ "$RUN_BACKEND" -eq 1 ]]; then
  echo "[1/5] 启动数据库（Docker 可用时自动拉起 MySQL 容器）..."
  bash "$ROOT_DIR/scripts/dev-db-up.sh"

  # 与 docker-compose 默认 root 密码对齐（容器运行时若未在 .env 中设置密码则使用 root）
  if docker ps --format '{{.Names}}' 2>/dev/null | grep -qx mienmien-mysql; then
    export DB_PASSWORD="${DB_PASSWORD:-root}"
  fi

  echo "[2/5] 初始化数据库结构..."
  bash "$ROOT_DIR/scripts/dev-seed.sh"
else
  echo "[1/5] 跳过数据库与后端初始化（DEV_UP_SKIP_BACKEND=1）"
fi

PIDS=()

if [[ "$RUN_BACKEND" -eq 1 ]]; then
  echo "[3/5] 启动 business 服务..."
  cd "$ROOT_DIR/java/business"
  nohup env DB_NAME="${DB_NAME:-MienMieApp}" DB_HOST="${DB_HOST:-localhost}" DB_PORT="${DB_PORT:-3306}" DB_USER="${DB_USER:-root}" DB_PASSWORD="${DB_PASSWORD:-}" mvn spring-boot:run > "$ROOT_DIR/.business.log" 2>&1 &
  BUSINESS_PID=$!
  PIDS+=("$BUSINESS_PID")

  echo "[4/5] 启动 consumer 服务..."
  # 百炼 Key 以 consumer 的 application.yml 为准；启动前丢弃可能从 .env 继承的旧变量，避免覆盖 yml。
  unset DASHSCOPE_API_KEY 2>/dev/null || true
  unset SPRING_AI_DASHSCOPE_API_KEY 2>/dev/null || true
  cd "$ROOT_DIR/java/consumer"
  nohup env \
    DB_NAME="${DB_NAME:-MienMieApp}" \
    DB_HOST="${DB_HOST:-localhost}" \
    DB_PORT="${DB_PORT:-3306}" \
    DB_USER="${DB_USER:-root}" \
    DB_PASSWORD="${DB_PASSWORD:-}" \
    VIDEO_INTERVIEW_DASHSCOPE_BASE_URL="${VIDEO_INTERVIEW_DASHSCOPE_BASE_URL:-}" \
    mvn spring-boot:run > "$ROOT_DIR/.consumer.log" 2>&1 &
  CONSUMER_PID=$!
  PIDS+=("$CONSUMER_PID")
else
  echo "[3/5] 跳过 business（DEV_UP_SKIP_BACKEND=1）"
  echo "[4/5] 跳过 consumer（DEV_UP_SKIP_BACKEND=1）"
fi

if [[ "$RUN_WEB" -eq 1 ]]; then
  echo "[5/5] 启动 web..."
  cd "$ROOT_DIR/web"
  npm install >/dev/null 2>&1 || true
  nohup npm run dev -- --host 0.0.0.0 > "$ROOT_DIR/.web.log" 2>&1 &
  WEB_PID=$!
  PIDS+=("$WEB_PID")
else
  echo "[5/5] 跳过 web（DEV_UP_SKIP_WEB=1）"
fi

printf "%s\n" "${PIDS[@]}" > "$ROOT_DIR/.dev-pids"

# 监听 Java 源码变更并增量编译，配合 spring-boot-devtools 触发热重启。
# 默认开启（DEV_BACKEND_COMPILE_WATCH 未设为 0 且本机已安装 fswatch）；关闭：DEV_BACKEND_COMPILE_WATCH=0 bash scripts/dev-up.sh
if [[ "$RUN_BACKEND" == "1" ]] && [[ "${DEV_BACKEND_COMPILE_WATCH:-1}" != "0" ]]; then
  if command -v fswatch >/dev/null 2>&1; then
    echo "[watch] fswatch → mvn compile（business / consumer），配合 devtools 自动重启"
    (
      cd "$ROOT_DIR/java/business"
      while fswatch -1 -l 1.2 ./src/main/java ./src/main/resources 2>/dev/null; do
        echo "[$(date '+%Y-%m-%d %H:%M:%S')] business compile" >>"$ROOT_DIR/.business-watch.log"
        mvn -q compile >>"$ROOT_DIR/.business-watch.log" 2>&1 || true
      done
    ) &
    echo $! >> "$ROOT_DIR/.dev-pids"
    (
      cd "$ROOT_DIR/java/consumer"
      while fswatch -1 -l 1.2 ./src/main/java ./src/main/resources 2>/dev/null; do
        echo "[$(date '+%Y-%m-%d %H:%M:%S')] consumer compile" >>"$ROOT_DIR/.consumer-watch.log"
        mvn -q compile >>"$ROOT_DIR/.consumer-watch.log" 2>&1 || true
      done
    ) &
    echo $! >> "$ROOT_DIR/.dev-pids"
  else
    echo "[watch] 未检测到 fswatch：已启用 spring-boot-devtools；另开终端在对应模块执行 mvn compile 亦可触发热重启。安装：brew install fswatch"
  fi
fi

echo "全部服务已启动"
if [[ "${BUSINESS_PID:-}" != "" ]]; then
  echo "business pid: $BUSINESS_PID"
fi
if [[ "${CONSUMER_PID:-}" != "" ]]; then
  echo "consumer pid: $CONSUMER_PID"
fi
if [[ "${WEB_PID:-}" != "" ]]; then
  echo "web pid: $WEB_PID"
fi
