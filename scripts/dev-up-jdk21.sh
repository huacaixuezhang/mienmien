#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"

if ! command -v /usr/libexec/java_home >/dev/null 2>&1; then
  echo "当前系统不支持 /usr/libexec/java_home，无法自动切换 JDK 21。"
  echo "请手动设置 JAVA_HOME 后执行：bash scripts/dev-up.sh"
  exit 1
fi

JAVA21_HOME="$(/usr/libexec/java_home -v 21 2>/dev/null || true)"
if [[ -z "${JAVA21_HOME}" ]]; then
  echo "未检测到本机安装 JDK 21。"
  echo "请先安装 JDK 21，再执行：bash scripts/dev-up-jdk21.sh"
  exit 1
fi

export JAVA_HOME="${JAVA21_HOME}"
export PATH="${JAVA_HOME}/bin:${PATH}"

echo "已切换 JAVA_HOME=${JAVA_HOME}"
echo "java -version:"
java -version 2>&1 | sed -n '1p'

bash "$ROOT_DIR/scripts/dev-up.sh"
