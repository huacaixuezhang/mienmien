# vision-requirements

## 变更信息

- 变更ID：REQ-0003
- 标题：语音模拟面试交互重构（流式出题、手动录音、轮次落库、Agent 四分拆、同轮重答、WS 单活）
- 日期：2026-05-09

## 愿景

- 以 consumer 为权威 FSM，前端按钮驱动录音与「下一题」；问题流式输出结束后即可作答（不等 TTS）；轮次写入 `mm_video_interview_turn`；不做 ISI/说话人识别与 iOS/微信专项。

## 需求摘要

- FR：流式问题、`post_turn_review` 内同轮重答与「下一题」、REST ASR、`turn_agent` 结构化落库、终局总评（`session_closing` 阶段异步生成，完成后 `ended`）、WS 单活、`GET /turns`。
- NFR：断线 `fromSeq` 重放、事件 seq 上限、无 Key 降级。
- Out：阿里云 ISI、iOS Safari/微信专项。

## DDD

- 限界上下文：consumer 视频面试运行时。
- 聚合：会话 + 轮次表 + 事件流；不变量：同会话单活跃 WS、FSM 合法迁移、`turn_id` 与当前轮一致。
