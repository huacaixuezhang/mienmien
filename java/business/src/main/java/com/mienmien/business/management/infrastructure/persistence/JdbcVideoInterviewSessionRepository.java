package com.mienmien.business.management.infrastructure.persistence;

import com.mienmien.business.management.domain.model.VideoInterviewSession;
import com.mienmien.business.management.domain.repository.VideoInterviewSessionRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public class JdbcVideoInterviewSessionRepository implements VideoInterviewSessionRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcVideoInterviewSessionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private static final RowMapper<VideoInterviewSession> MAPPER = (rs, rowNum) ->
            VideoInterviewSession.restoreRow(
                    rs.getString("session_id"),
                    rs.getString("user_id"),
                    rs.getString("space_id"),
                    rs.getString("business_record_id"),
                    rs.getString("position_id"),
                    rs.getInt("round_index"),
                    rs.getString("style_key"),
                    rs.getString("status"),
                    rs.getLong("epoch"),
                    rs.getString("resume_snapshot_json"),
                    rs.getString("job_snapshot_json"),
                    rs.getString("style_prompt_snapshot"),
                    rs.getString("orchestrator_model"),
                    rs.getString("asr_model"),
                    rs.getTimestamp("started_at").toInstant(),
                    rs.getTimestamp("ended_at") != null ? rs.getTimestamp("ended_at").toInstant() : null);

    @Override
    public void insert(VideoInterviewSession s) {
        jdbcTemplate.update(
                """
                        INSERT INTO mm_video_interview_session
                        (session_id, user_id, space_id, business_record_id, position_id, round_index, style_key,
                         status, epoch, last_event_seq, resume_snapshot_json, job_snapshot_json, style_prompt_snapshot,
                         orchestrator_model, asr_model, started_at, ended_at)
                        VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                        """,
                s.getSessionId(),
                s.getUserId(),
                s.getSpaceId(),
                s.getBusinessRecordId(),
                s.getPositionIdOrNull(),
                s.getRoundIndex(),
                s.getStyleKey(),
                s.getStatus(),
                s.getEpoch(),
                0L,
                s.getResumeSnapshotJson(),
                s.getJobSnapshotJson(),
                s.getStylePromptSnapshot(),
                s.getOrchestratorModel(),
                s.getAsrModel(),
                Timestamp.from(s.getStartedAt()),
                s.getEndedAtOrNull() == null ? null : Timestamp.from(s.getEndedAtOrNull()));
    }

    @Override
    public Optional<VideoInterviewSession> findBySessionId(String sessionId) {
        List<VideoInterviewSession> list =
                jdbcTemplate.query(
                        """
                                SELECT session_id, user_id, space_id, business_record_id, position_id, round_index,
                                       style_key, status, epoch, resume_snapshot_json, job_snapshot_json,
                                       style_prompt_snapshot, orchestrator_model, asr_model, started_at, ended_at
                                FROM mm_video_interview_session WHERE session_id = ?
                                """,
                        MAPPER,
                        sessionId);
        return list.stream().findFirst();
    }
}
