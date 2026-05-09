package com.mienmien.consumer.guidance.infrastructure.persistence;

import com.mienmien.consumer.guidance.domain.model.QuestionEvent;
import com.mienmien.consumer.guidance.domain.repository.QuestionEventRepository;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.Optional;

@Repository
public class JdbcQuestionEventRepository implements QuestionEventRepository {
    private final JdbcTemplate jdbcTemplate;

    public JdbcQuestionEventRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void save(QuestionEvent event) {
        jdbcTemplate.update(
                "INSERT INTO mm_question_event(event_id, session_id, source, question_text, created_at) VALUES (?, ?, ?, ?, ?)",
                event.getEventId(),
                event.getSessionId(),
                event.getSource(),
                event.getQuestionText(),
                Timestamp.from(event.getCreatedAt())
        );
    }

    @Override
    public Optional<String> findLatestQuestionText(String sessionId) {
        try {
            String text = jdbcTemplate.queryForObject(
                    "SELECT question_text FROM mm_question_event WHERE session_id = ? ORDER BY created_at DESC LIMIT 1",
                    String.class,
                    sessionId
            );
            return Optional.ofNullable(text);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<String> findLatestQuestionEventId(String sessionId) {
        try {
            String id = jdbcTemplate.queryForObject(
                    "SELECT event_id FROM mm_question_event WHERE session_id = ? ORDER BY created_at DESC LIMIT 1",
                    String.class,
                    sessionId
            );
            return Optional.ofNullable(id);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }
}
