# plan-task

## 变更 ID

REQ-0003

## 任务清单

1. `scripts/migrate-mm-video-interview-turn.sql` + consumer/business schema bootstrap 增加 `mm_video_interview_turn`。
2. `VideoInterviewJdbcStore` 轮次 CRUD + `listTurns`。
3. `DashscopeCompatibleChatClient` SSE 流式 + `VideoInterviewTurnAgentService`。
4. `VideoInterviewRuntimeService` 新 FSM、`post_turn_review`、`continue_next`、`turn_retry_same`、WS 单活。
5. `VideoInterviewRestController` `GET .../sessions/{id}/turns`。
6. 前端 `api.js` + `VideoInterviewRoom.vue` + `mockInterviewFsm.js` 映射。

## 技术要点（与实现对齐）

- 会话状态 `session_closing`：已进入终局流程、异步生成总评；此期间拒绝业务类 WS 消息（`ping` / 重复的 `end_interview` 除外）。
- 「判停后总评前可重答」：仅在 `post_turn_review` 且尚未触发终局路径（未进入 `session_closing`）时允许 `turn_retry_same`。

## 验收

- 编译通过；首连流式首题 → 回顾 → 下一题 / 同轮重答；判停后总评前可重答；触发结束后面见「终局总评生成中」再收到 `ended`；双开标签旧连接被关。
