#!/usr/bin/env bash
# 供 dev-check 等脚本 source：从 stdin 读取 JSON，输出顶层字符串字段。
# 优先 node，其次 jq；均无则返回非零。
dev_json_string_field() {
  local field="$1"
  if command -v node >/dev/null 2>&1; then
    node -e '
      const fs = require("fs");
      const field = process.argv[1];
      const d = JSON.parse(fs.readFileSync(0, "utf8"));
      const v = d[field];
      process.stdout.write(v == null || v === undefined ? "" : String(v));
    ' "$field"
    return 0
  fi
  if command -v jq >/dev/null 2>&1; then
    jq -r --arg k "$field" '.[$k] // empty'
    return 0
  fi
  echo "[dev-json] 解析 JSON 需要安装 node 或 jq（字段: $field）" >&2
  return 1
}
