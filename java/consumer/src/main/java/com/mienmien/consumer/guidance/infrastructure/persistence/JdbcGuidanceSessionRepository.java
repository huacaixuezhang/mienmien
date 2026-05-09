package com.mienmien.consumer.guidance.infrastructure.persistence;

import com.mienmien.consumer.guidance.domain.model.GuidanceSession;
import com.mienmien.consumer.guidance.domain.repository.GuidanceSessionRepository;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

@Repository
public class JdbcGuidanceSessionRepository implements GuidanceSessionRepository {
    private final JdbcTemplate jdbcTemplate;

    public JdbcGuidanceSessionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void save(GuidanceSession session) {
        jdbcTemplate.update(
                "INSERT INTO mm_guidance_session(session_id, user_id, mode, status, started_at, ended_at) VALUES (?, ?, ?, ?, ?, ?)",
                session.getSessionId(),
                session.getUserId(),
                session.getMode(),
                session.getStatus(),
                Timestamp.from(session.getStartedAt()),
                session.getEndedAt() == null ? null : Timestamp.from(session.getEndedAt())
        );
    }

    @Override
    public void updateStatus(String sessionId, String status) {
        jdbcTemplate.update("UPDATE mm_guidance_session SET status = ? WHERE session_id = ?", status, sessionId);
    }

    @Override
    public Optional<GuidanceSession> findById(String sessionId) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                    "SELECT session_id, user_id, mode, status, started_at, ended_at FROM mm_guidance_session WHERE session_id = ?",
                    (rs, rowNum) -> GuidanceSession.restore(
                            rs.getString("session_id"),
                            rs.getString("user_id"),
                            rs.getString("mode"),
                            rs.getString("status"),
                            rs.getTimestamp("started_at").toInstant(),
                            rs.getTimestamp("ended_at") == null ? null : rs.getTimestamp("ended_at").toInstant()
                    ),
                    sessionId
            ));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    @Override
    public void endSession(String sessionId, Instant endedAt) {
        jdbcTemplate.update(
                "UPDATE mm_guidance_session SET status = 'completed', ended_at = ? WHERE session_id = ?",
                Timestamp.from(endedAt),
                sessionId
        );
    }
}
