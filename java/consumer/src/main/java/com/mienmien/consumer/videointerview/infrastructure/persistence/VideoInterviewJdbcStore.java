package com.mienmien.consumer.videointerview.infrastructure.persistence;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Component
public class VideoInterviewJdbcStore {

    private final JdbcTemplate jdbcTemplate;

    public VideoInterviewJdbcStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<String> findUserIdByBusinessSessionToken(String sessionToken) {
        if (sessionToken == null || sessionToken.isBlank()) {
            return Optional.empty();
        }
        List<String> rows =
                jdbcTemplate.query(
                        """
                                SELECT user_id FROM mm_business_session
                                WHERE session_token = ? AND expires_at > NOW()
                                LIMIT 1
                                """,
                        (rs, rowNum) -> rs.getString("user_id"),
                        sessionToken);
        return rows.isEmpty() ? Optional.empty() : Optional.ofNullable(rows.get(0));
    }

    public Optional<VideoInterviewSessionRow> loadSession(String sessionId) {
        List<VideoInterviewSessionRow> list =
                jdbcTemplate.query(
                        """
                                SELECT session_id, user_id, space_id, business_record_id, position_id, round_index,
                                       style_key, status, epoch, last_event_seq, resume_snapshot_json, job_snapshot_json,
                                       style_prompt_snapshot, orchestrator_model, asr_model, started_at, ended_at
                                FROM mm_video_interview_session WHERE session_id = ?
                                """,
                        SESSION_ROW_MAPPER,
                        sessionId);
        return list.stream().findFirst();
    }

    public void updateSessionStatus(String sessionId, String status, Instant endedAtOrNull) {
        jdbcTemplate.update(
                "UPDATE mm_video_interview_session SET status = ?, ended_at = ? WHERE session_id = ?",
                status,
                endedAtOrNull == null ? null : Timestamp.from(endedAtOrNull),
                sessionId);
    }

    public void bumpEpoch(String sessionId, long epoch) {
        jdbcTemplate.update("UPDATE mm_video_interview_session SET epoch = ? WHERE session_id = ?", epoch, sessionId);
    }

    public long appendEvent(String sessionId, String eventId, String type, String payloadJson) {
        jdbcTemplate.update(
                "UPDATE mm_video_interview_session SET last_event_seq = last_event_seq + 1 WHERE session_id = ?",
                sessionId);
        Long seq =
                jdbcTemplate.queryForObject(
                        "SELECT last_event_seq FROM mm_video_interview_session WHERE session_id = ?",
                        Long.class,
                        sessionId);
        if (seq == null) {
            throw new IllegalStateException("last_event_seq missing");
        }
        jdbcTemplate.update(
                """
                        INSERT INTO mm_video_interview_event (event_id, session_id, seq, type, payload_json)
                        VALUES (?,?,?,?,?)
                        """,
                eventId,
                sessionId,
                seq,
                type,
                payloadJson);
        return seq;
    }

    public List<VideoInterviewEventRow> listEventsAfter(String sessionId, long minSeqExclusive) {
        return jdbcTemplate.query(
                """
                        SELECT event_id, session_id, seq, type, payload_json, created_at
                        FROM mm_video_interview_event
                        WHERE session_id = ? AND seq > ?
                        ORDER BY seq ASC
                        """,
                EVENT_ROW_MAPPER,
                sessionId,
                minSeqExclusive);
    }

    public String loadInterviewSummary(String recordId) {
        List<String> rows =
                jdbcTemplate.query(
                        "SELECT summary FROM mm_interview_record WHERE record_id = ? LIMIT 1",
                        (rs, rowNum) -> rs.getString("summary"),
                        recordId);
        return rows.isEmpty() ? "" : rows.get(0) == null ? "" : rows.get(0);
    }

    public void updateInterviewSummary(String recordId, String summaryText) {
        jdbcTemplate.update("UPDATE mm_interview_record SET summary = ? WHERE record_id = ?", summaryText, recordId);
    }

    /** 与 summary 中末轮结论对齐，供列表等读取 {@code result}（passed / failed / pending）。 */
    public void patchInterviewRecordResult(String recordId, String result) {
        if (recordId == null || recordId.isBlank() || result == null || result.isBlank()) {
            return;
        }
        jdbcTemplate.update("UPDATE mm_interview_record SET result = ? WHERE record_id = ?", result, recordId);
    }

    public void insertTurn(String turnId, String sessionId, int turnIndex) {
        jdbcTemplate.update(
                """
                        INSERT INTO mm_video_interview_turn (turn_id, session_id, turn_index, question_text)
                        VALUES (?,?,?,?)
                        """,
                turnId,
                sessionId,
                turnIndex,
                "");
    }

    public void updateTurnQuestionText(String turnId, String questionText) {
        jdbcTemplate.update(
                "UPDATE mm_video_interview_turn SET question_text = ? WHERE turn_id = ?",
                questionText == null ? "" : questionText,
                turnId);
    }

    public void updateTurnAnswerText(String turnId, String answerText) {
        jdbcTemplate.update(
                """
                        UPDATE mm_video_interview_turn
                        SET answer_text = ?, answered_at = CURRENT_TIMESTAMP
                        WHERE turn_id = ?
                        """,
                answerText == null ? "" : answerText,
                turnId);
    }

    public void updateTurnAgentFields(
            String turnId, String bridgingUtterance, String evaluationJson, String standardAnswer, String agentRawJson) {
        jdbcTemplate.update(
                """
                        UPDATE mm_video_interview_turn
                        SET bridging_utterance = ?, evaluation_json = ?, standard_answer = ?, agent_raw_json = ?
                        WHERE turn_id = ?
                        """,
                bridgingUtterance,
                evaluationJson,
                standardAnswer,
                agentRawJson,
                turnId);
    }

    /** Agent 后同轮重答：清空作答与模型产物，递增 answer_attempt */
    public void clearTurnForSameRoundRetry(String turnId) {
        jdbcTemplate.update(
                """
                        UPDATE mm_video_interview_turn
                        SET answer_text = NULL,
                            standard_answer = NULL,
                            evaluation_json = NULL,
                            agent_raw_json = NULL,
                            bridging_utterance = NULL,
                            answered_at = NULL,
                            answer_attempt = answer_attempt + 1
                        WHERE turn_id = ?
                        """,
                turnId);
    }

    public int maxTurnIndex(String sessionId) {
        Integer v =
                jdbcTemplate.queryForObject(
                        "SELECT COALESCE(MAX(turn_index), 0) FROM mm_video_interview_turn WHERE session_id = ?",
                        Integer.class,
                        sessionId);
        return v == null ? 0 : v;
    }

    public List<VideoInterviewTurnRow> listTurns(String sessionId) {
        return jdbcTemplate.query(
                """
                        SELECT turn_id, session_id, turn_index, question_text, answer_text, standard_answer,
                               evaluation_json, agent_raw_json, bridging_utterance, answer_attempt, created_at, answered_at
                        FROM mm_video_interview_turn
                        WHERE session_id = ?
                        ORDER BY turn_index ASC
                        """,
                TURN_ROW_MAPPER,
                sessionId);
    }

    public Optional<VideoInterviewTurnRow> loadTurn(String turnId) {
        List<VideoInterviewTurnRow> rows =
                jdbcTemplate.query(
                        """
                                SELECT turn_id, session_id, turn_index, question_text, answer_text, standard_answer,
                                       evaluation_json, agent_raw_json, bridging_utterance, answer_attempt, created_at, answered_at
                                FROM mm_video_interview_turn WHERE turn_id = ?
                                """,
                        TURN_ROW_MAPPER,
                        turnId);
        return rows.stream().findFirst();
    }

    private static final RowMapper<VideoInterviewTurnRow> TURN_ROW_MAPPER =
            (rs, rowNum) ->
                    new VideoInterviewTurnRow(
                            rs.getString("turn_id"),
                            rs.getString("session_id"),
                            rs.getInt("turn_index"),
                            rs.getString("question_text"),
                            rs.getString("answer_text"),
                            rs.getString("standard_answer"),
                            rs.getString("evaluation_json"),
                            rs.getString("agent_raw_json"),
                            rs.getString("bridging_utterance"),
                            rs.getInt("answer_attempt"),
                            rs.getTimestamp("created_at").toInstant(),
                            rs.getTimestamp("answered_at") != null ? rs.getTimestamp("answered_at").toInstant() : null);

    public record VideoInterviewTurnRow(
            String turnId,
            String sessionId,
            int turnIndex,
            String questionText,
            String answerText,
            String standardAnswer,
            String evaluationJson,
            String agentRawJson,
            String bridgingUtterance,
            int answerAttempt,
            Instant createdAt,
            Instant answeredAt) {
    }

    private static final RowMapper<VideoInterviewSessionRow> SESSION_ROW_MAPPER =
            (rs, rowNum) ->
                    new VideoInterviewSessionRow(
                            rs.getString("session_id"),
                            rs.getString("user_id"),
                            rs.getString("space_id"),
                            rs.getString("business_record_id"),
                            rs.getString("position_id"),
                            rs.getInt("round_index"),
                            rs.getString("style_key"),
                            rs.getString("status"),
                            rs.getLong("epoch"),
                            rs.getLong("last_event_seq"),
                            rs.getString("resume_snapshot_json"),
                            rs.getString("job_snapshot_json"),
                            rs.getString("style_prompt_snapshot"),
                            rs.getString("orchestrator_model"),
                            rs.getString("asr_model"),
                            rs.getTimestamp("started_at").toInstant(),
                            rs.getTimestamp("ended_at") != null ? rs.getTimestamp("ended_at").toInstant() : null);

    private static final RowMapper<VideoInterviewEventRow> EVENT_ROW_MAPPER =
            (rs, rowNum) ->
                    new VideoInterviewEventRow(
                            rs.getString("event_id"),
                            rs.getString("session_id"),
                            rs.getLong("seq"),
                            rs.getString("type"),
                            rs.getString("payload_json"),
                            rs.getTimestamp("created_at").toInstant());

    public record VideoInterviewSessionRow(
            String sessionId,
            String userId,
            String spaceId,
            String businessRecordId,
            String positionId,
            int roundIndex,
            String styleKey,
            String status,
            long epoch,
            long lastEventSeq,
            String resumeSnapshotJson,
            String jobSnapshotJson,
            String stylePromptSnapshot,
            String orchestratorModel,
            String asrModel,
            Instant startedAt,
            Instant endedAt) {
    }

    public record VideoInterviewEventRow(
            String eventId, String sessionId, long seq, String type, String payloadJson, Instant createdAt) {
    }
}
