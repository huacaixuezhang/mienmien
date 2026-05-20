/**
 * 语音模拟面试客户端状态（以服务端 `state` 事件为准）。
 */
export const InterviewState = {
  IDLE: "idle",
  READY: "ready",
  QUESTION_STREAMING: "question_streaming",
  AI_SPEAKING: "ai_speaking",
  AWAITING_ANSWER: "awaiting_answer",
  RECORDING: "recording",
  USER_ANSWERING: "user_answering",
  AGENT_PROCESSING: "agent_processing",
  POST_TURN_REVIEW: "post_turn_review",
  /** 与 consumer `session_closing` 对齐：终局总评异步生成中 */
  SESSION_CLOSING: "session_closing",
  JUDGING: "judging",
  END: "end"
};

/** 将服务端 state.payload.state 映射到客户端展示状态 */
export function mapServerStateToFsm(serverState) {
  const s = String(serverState || "").trim();
  if (s === "ended") return InterviewState.END;
  if (s === "question_streaming") return InterviewState.QUESTION_STREAMING;
  if (s === "awaiting_answer") return InterviewState.AWAITING_ANSWER;
  if (s === "recording") return InterviewState.RECORDING;
  if (s === "agent_processing") return InterviewState.AGENT_PROCESSING;
  if (s === "post_turn_review") return InterviewState.POST_TURN_REVIEW;
  if (s === "session_closing") return InterviewState.SESSION_CLOSING;
  if (s === "interviewer_speaking" || s === "listening_user") return InterviewState.USER_ANSWERING;
  if (s === "in_progress") return InterviewState.READY;
  return InterviewState.READY;
}

export function fsmLabelZh(state) {
  switch (state) {
    case InterviewState.IDLE:
      return "未开始";
    case InterviewState.READY:
      return "已连接 / 准备";
    case InterviewState.QUESTION_STREAMING:
      return "题目生成中";
    case InterviewState.AI_SPEAKING:
      return "面试官发言中";
    case InterviewState.AWAITING_ANSWER:
      return "可开始作答";
    case InterviewState.RECORDING:
      return "录音中";
    case InterviewState.USER_ANSWERING:
      return "作答中（兼容）";
    case InterviewState.AGENT_PROCESSING:
      return "评价与出题中";
    case InterviewState.POST_TURN_REVIEW:
      return "本轮已答 · 请继续";
    case InterviewState.SESSION_CLOSING:
      return "生成中";
    case InterviewState.JUDGING:
      return "判定中（兼容）";
    case InterviewState.END:
      return "生成完成";
    default:
      return String(state);
  }
}
