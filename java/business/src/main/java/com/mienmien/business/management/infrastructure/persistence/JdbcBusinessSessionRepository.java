package com.mienmien.business.management.infrastructure.persistence;

import com.mienmien.business.management.domain.repository.BusinessSessionRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public class JdbcBusinessSessionRepository implements BusinessSessionRepository {
    private final JdbcTemplate jdbcTemplate;

    public JdbcBusinessSessionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void save(String sessionToken, String userId, Instant expiresAt) {
        jdbcTemplate.update(
                "INSERT INTO mm_business_session (session_token, user_id, expires_at) VALUES (?,?,?)",
                sessionToken,
                userId,
                Timestamp.from(expiresAt)
        );
    }

    @Override
    public Optional<String> findUserIdByValidToken(String sessionToken) {
        if (sessionToken == null || sessionToken.isBlank()) {
            return Optional.empty();
        }
        List<String> rows = jdbcTemplate.query(
                """
                        SELECT user_id FROM mm_business_session
                        WHERE session_token = ? AND expires_at > NOW()
                        LIMIT 1
                        """,
                (rs, rowNum) -> rs.getString("user_id"),
                sessionToken
        );
        return rows.isEmpty() ? Optional.empty() : Optional.ofNullable(rows.get(0));
    }

    @Override
    public void deleteByToken(String sessionToken) {
        if (sessionToken == null || sessionToken.isBlank()) {
            return;
        }
        jdbcTemplate.update("DELETE FROM mm_business_session WHERE session_token = ?", sessionToken);
    }
}
