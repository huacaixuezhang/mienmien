package com.mienmien.business.management.infrastructure.persistence;

import com.mienmien.business.management.domain.repository.BusinessSessionRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
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
                "INSERT INTO mm_business_session(session_token, user_id, created_at, expires_at) VALUES (?, ?, ?, ?)",
                sessionToken,
                userId,
                Timestamp.from(Instant.now()),
                Timestamp.from(expiresAt)
        );
    }

    @Override
    public Optional<String> findUserIdByValidToken(String sessionToken) {
        try {
            String userId = jdbcTemplate.queryForObject(
                    "SELECT user_id FROM mm_business_session WHERE session_token = ? AND expires_at > ?",
                    String.class,
                    sessionToken,
                    Timestamp.from(Instant.now())
            );
            return Optional.ofNullable(userId);
        } catch (org.springframework.dao.EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    @Override
    public void deleteByToken(String sessionToken) {
        jdbcTemplate.update("DELETE FROM mm_business_session WHERE session_token = ?", sessionToken);
    }
}
