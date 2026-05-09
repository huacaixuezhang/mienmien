#!/usr/bin/env bash
set -euo pipefail

show_port() {
  local port="$1"
  local name="$2"
  local out
  out="$(lsof -nP -iTCP:"$port" -sTCP:LISTEN 2>/dev/null || true)"
  if [[ -n "$out" ]]; then
    echo "[status] ${name} : LISTEN on :${port}"
    echo "$out" | awk 'NR==2{print "         pid=" $2 ", cmd=" $1}'
  else
    echo "[status] ${name} : NOT LISTENING on :${port}"
  fi
}

echo "[status] process ports"
show_port 8080 "business"
show_port 8081 "consumer"
show_port 5173 "web"

echo "[status] actuator health"
for url in "http://localhost:8080/actuator/health" "http://localhost:8081/actuator/health"; do
  code="$(curl -s -o /tmp/mm-health.out -w "%{http_code}" "$url" || true)"
  echo "  $url -> HTTP ${code}"
done

if [[ -f ".dev-pids" ]]; then
  echo "[status] .dev-pids"
  awk '{print "  pid=" $1}' .dev-pids
else
  echo "[status] .dev-pids not found"
fi
