#!/usr/bin/env bash
set -euo pipefail

echo "[check] health endpoints"
curl -fsS http://localhost:8080/actuator/health >/dev/null
curl -fsS http://localhost:8081/actuator/health >/dev/null

echo "[check] business login (seed: 13800138000 / dev123456)"
LOGIN_JSON="$(curl -fsS -X POST http://localhost:8080/api/v1/business/auth/login -H "Content-Type: application/json" -d '{"phone":"13800138000","password":"dev123456"}')"
TOKEN="$(printf '%s' "$LOGIN_JSON" | node -e "const fs=require('fs'); const d=JSON.parse(fs.readFileSync(0,'utf8')); console.log(d.sessionToken || '');")"
if [[ -z "$TOKEN" ]]; then
  echo "login failed, no sessionToken"
  echo "$LOGIN_JSON"
  exit 1
fi
BIZ_AUTH=( -H "Authorization: Bearer $TOKEN" )

echo "[check] create space"
SPACE_JSON="$(curl -fsS -X POST http://localhost:8080/api/v1/business/spaces -H "Content-Type: application/json" "${BIZ_AUTH[@]}" -d '{"name":"全量验收空间"}')"
echo "$SPACE_JSON"
SPACE_ID="$(printf '%s' "$SPACE_JSON" | node -e "const fs=require('fs'); const d=JSON.parse(fs.readFileSync(0,'utf8')); console.log(d.spaceId);")"

echo "[check] B resume / jd / interview smoke"
curl -fsS -X POST http://localhost:8080/api/v1/business/resumes -H "Content-Type: application/json" "${BIZ_AUTH[@]}" \
  -d "{\"spaceId\":\"$SPACE_ID\",\"content\":\"hello\",\"version\":1}" >/dev/null
curl -fsS "http://localhost:8080/api/v1/business/resumes/$SPACE_ID" "${BIZ_AUTH[@]}" >/dev/null
curl -fsS -X POST http://localhost:8080/api/v1/business/jd-targets -H "Content-Type: application/json" "${BIZ_AUTH[@]}" \
  -d "{\"spaceId\":\"$SPACE_ID\",\"rawText\":\"jd\",\"focusPoints\":\"fp\"}" >/dev/null
curl -fsS "http://localhost:8080/api/v1/business/jd-targets/$SPACE_ID" "${BIZ_AUTH[@]}" >/dev/null
curl -fsS -X POST "http://localhost:8080/api/v1/business/interviews/mock" -H "Content-Type: application/json" "${BIZ_AUTH[@]}" \
  -d "{\"spaceId\":\"$SPACE_ID\"}" >/dev/null
curl -fsS -X POST "http://localhost:8080/api/v1/business/interviews/real" -H "Content-Type: application/json" "${BIZ_AUTH[@]}" \
  -d "{\"spaceId\":\"$SPACE_ID\"}" >/dev/null
curl -fsS "http://localhost:8080/api/v1/business/interviews/$SPACE_ID" "${BIZ_AUTH[@]}" >/dev/null

echo "[check] rename space"
curl -fsS -X PUT "http://localhost:8080/api/v1/business/spaces/$SPACE_ID" -H "Content-Type: application/json" "${BIZ_AUTH[@]}" -d '{"name":"重命名验收空间"}' >/dev/null

echo "[check] delete empty space -> 200"
EMPTY_SPACE_JSON="$(curl -fsS -X POST http://localhost:8080/api/v1/business/spaces -H "Content-Type: application/json" "${BIZ_AUTH[@]}" -d '{"name":"待删除空间"}')"
EMPTY_SPACE_ID="$(printf '%s' "$EMPTY_SPACE_JSON" | node -e "const fs=require('fs'); const d=JSON.parse(fs.readFileSync(0,'utf8')); console.log(d.spaceId);")"
curl -fsS -X DELETE "http://localhost:8080/api/v1/business/spaces/$EMPTY_SPACE_ID/hard" "${BIZ_AUTH[@]}" >/dev/null

echo "[check] job positions"
JP_JSON="$(curl -fsS -X POST http://localhost:8080/api/v1/business/job-positions -H "Content-Type: application/json" "${BIZ_AUTH[@]}" -d "{\"spaceId\":\"$SPACE_ID\",\"title\":\"后端工程师\",\"company\":\"Acme\",\"location\":\"上海\",\"baseRange\":\"30-50K\"}")"
echo "$JP_JSON"
JP_ID="$(printf '%s' "$JP_JSON" | node -e "const fs=require('fs'); const d=JSON.parse(fs.readFileSync(0,'utf8')); console.log(d.positionId);")"
curl -fsS "http://localhost:8080/api/v1/business/job-positions/$SPACE_ID" "${BIZ_AUTH[@]}" >/dev/null
curl -fsS -X DELETE "http://localhost:8080/api/v1/business/job-positions/item/$JP_ID" "${BIZ_AUTH[@]}" >/dev/null

echo "[check] standard answer bank"
curl -fsS -X PUT http://localhost:8080/api/v1/business/answer-banks -H "Content-Type: application/json" "${BIZ_AUTH[@]}" \
  -d "{\"spaceId\":\"$SPACE_ID\",\"intro\":\"i\",\"reason\":\"r\",\"strengths\":\"s\",\"project\":\"p\",\"hr\":\"h\"}" >/dev/null
curl -fsS "http://localhost:8080/api/v1/business/answer-banks/$SPACE_ID" "${BIZ_AUTH[@]}" >/dev/null

echo "[check] resume version conflict -> 409"
RES_V=$((RANDOM % 80000 + 10000))
curl -fsS -X POST http://localhost:8080/api/v1/business/resumes -H "Content-Type: application/json" "${BIZ_AUTH[@]}" -d "{\"spaceId\":\"$SPACE_ID\",\"content\":\"v${RES_V}\",\"version\":${RES_V}}" >/dev/null
C409="$(curl -s -o /tmp/mm-res-dup.json -w "%{http_code}" -X POST http://localhost:8080/api/v1/business/resumes -H "Content-Type: application/json" "${BIZ_AUTH[@]}" -d "{\"spaceId\":\"$SPACE_ID\",\"content\":\"dup\",\"version\":${RES_V}}")"
if [[ "$C409" != "409" ]]; then
  echo "expected HTTP 409 duplicate resume version, got $C409"
  cat /tmp/mm-res-dup.json || true
  exit 1
fi

echo "[check] consumer stream health"
curl -fsS http://localhost:8081/api/v1/consumer/health/stream >/dev/null

echo "[check] invalid consumer mode -> 400"
BAD_CODE="$(curl -s -o /tmp/mm-bad-mode.json -w "%{http_code}" -X POST http://localhost:8081/api/v1/consumer/sessions -H "Content-Type: application/json" -d '{"userId":"user_001","mode":"invalid"}')"
if [[ "$BAD_CODE" != "400" ]]; then
  echo "expected HTTP 400 for invalid mode, got $BAD_CODE"
  cat /tmp/mm-bad-mode.json || true
  exit 1
fi

echo "[check] create session"
SESSION_JSON="$(curl -fsS -X POST http://localhost:8081/api/v1/consumer/sessions -H "Content-Type: application/json" -d '{"userId":"user_001","mode":"live"}')"
echo "$SESSION_JSON"
SESSION_ID="$(printf '%s' "$SESSION_JSON" | node -e "const fs=require('fs'); const d=JSON.parse(fs.readFileSync(0,'utf8')); console.log(d.sessionId);")"

echo "[check] unknown session text event -> 404"
U_CODE="$(curl -s -o /tmp/mm-unknown-sess.json -w "%{http_code}" -X POST "http://localhost:8081/api/v1/consumer/sessions/gs_nonexistent_zzzz/events/text" -H "Content-Type: application/json" -d '{"questionText":"hi"}')"
if [[ "$U_CODE" != "404" ]]; then
  echo "expected HTTP 404 for unknown session, got $U_CODE"
  cat /tmp/mm-unknown-sess.json || true
  exit 1
fi

echo "[check] voice event"
curl -fsS -X POST "http://localhost:8081/api/v1/consumer/sessions/$SESSION_ID/events/voice" -H "Content-Type: application/json" -d '{"questionText":"请做一个自我介绍"}'

echo "[check] photo event"
curl -fsS -X POST "http://localhost:8081/api/v1/consumer/sessions/$SESSION_ID/events/photo" -H "Content-Type: application/json" -d '{"questionText":"图片相关问题"}'

echo "[check] text event"
curl -fsS -X POST "http://localhost:8081/api/v1/consumer/sessions/$SESSION_ID/events/text" -H "Content-Type: application/json" -d '{"questionText":"手写补充问题"}'

echo "[check] photo-qa"
curl -fsS "http://localhost:8081/api/v1/consumer/sessions/$SESSION_ID/photo-qa"

echo "[check] answers once"
curl -fsS "http://localhost:8081/api/v1/consumer/sessions/$SESSION_ID/answers/once"

echo "[check] sse stream (max 8s) assert events"
SSE_OUT="$(curl -fsS -N --max-time 8 -H "Accept: text/event-stream" "http://localhost:8081/api/v1/consumer/sessions/$SESSION_ID/answers/stream" || true)"
printf '%s\n' "$SSE_OUT" | tee /tmp/mm-sse.out
if ! grep -qE '^event:(chunk|fallback)' /tmp/mm-sse.out; then
  echo "SSE 输出缺少 chunk 或 fallback 事件"
  exit 1
fi
if ! grep -qE '^event:(done|fallback)' /tmp/mm-sse.out; then
  echo "SSE 输出缺少 done 或 fallback 事件"
  exit 1
fi

echo "[check] session end + blocked text -> 409"
SESSION2_JSON="$(curl -fsS -X POST http://localhost:8081/api/v1/consumer/sessions -H "Content-Type: application/json" -d '{"userId":"user_001","mode":"live"}')"
SESSION2_ID="$(printf '%s' "$SESSION2_JSON" | node -e "const fs=require('fs'); const d=JSON.parse(fs.readFileSync(0,'utf8')); console.log(d.sessionId);")"
curl -fsS -X POST "http://localhost:8081/api/v1/consumer/sessions/$SESSION2_ID/end"
E_CODE="$(curl -s -o /tmp/mm-after-end.json -w "%{http_code}" -X POST "http://localhost:8081/api/v1/consumer/sessions/$SESSION2_ID/events/text" -H "Content-Type: application/json" -d '{"questionText":"不应成功"}')"
if [[ "$E_CODE" != "409" ]]; then
  echo "expected HTTP 409 after session end, got $E_CODE"
  cat /tmp/mm-after-end.json || true
  exit 1
fi

echo "[check] DONE"
