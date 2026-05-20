-- 语音模拟面试轮次：面试官衔接语（导演 B，与 TurnAgent 独立落库）
-- mysql ... < scripts/migrate-mm-video-interview-turn-bridging.sql

ALTER TABLE mm_video_interview_turn
  ADD COLUMN bridging_utterance VARCHAR(2000) NULL AFTER agent_raw_json;
