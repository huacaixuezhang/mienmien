#!/usr/bin/env bash
set -euo pipefail

missing=0
for cmd in java mvn node npm curl; do
  if ! command -v "$cmd" >/dev/null 2>&1; then
    echo "缺少命令: $cmd"
    missing=1
  fi
done

if ! java -version >/dev/null 2>&1; then
  echo "Java 运行时不可用，请安装 JDK 21。"
  missing=1
else
  if ! java -version 2>&1 | grep -qE 'version "21|version "2[1-9]'; then
    echo "警告：建议使用 JDK 21。当前 java -version："
    java -version 2>&1 | head -1 || true
    if command -v /usr/libexec/java_home >/dev/null 2>&1; then
      if /usr/libexec/java_home -v 21 >/dev/null 2>&1; then
        echo "提示：本机已安装 JDK 21，可在执行 dev-up 前运行：export JAVA_HOME=\"\$(/usr/libexec/java_home -v 21)\""
      fi
    fi
  fi
fi

db_ok=0
if command -v mysql >/dev/null 2>&1; then
  db_ok=1
fi
if command -v docker >/dev/null 2>&1; then
  db_ok=1
fi
if [[ "$db_ok" -eq 0 ]]; then
  echo "数据库环境：需要「mysql 客户端」或「docker」（用于 docker compose 启动 MySQL）。"
  missing=1
fi

if [[ "$missing" -ne 0 ]]; then
  echo "请先安装缺失依赖后再执行 dev-up。"
  exit 1
fi

if ! command -v node >/dev/null 2>&1 && ! command -v jq >/dev/null 2>&1; then
  echo "提示：验收脚本 dev-check 解析 JSON 需要 node 或 jq（通常已随 Node 安装 node）。"
fi

echo "依赖检查通过。"
