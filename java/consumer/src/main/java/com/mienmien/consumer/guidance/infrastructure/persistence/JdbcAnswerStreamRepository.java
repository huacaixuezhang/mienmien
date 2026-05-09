package com.mienmien.consumer.guidance.infrastructure.persistence;

import com.mienmien.consumer.guidance.domain.repository.AnswerStreamRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcAnswerStreamRepository implements AnswerStreamRepository {
    private final JdbcTemplate jdbcTemplate;

    public JdbcAnswerStreamRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void saveWithLatestEvent(String streamId, String sessionId, String finalAnswer) {
        jdbcTemplate.update(
                "INSERT INTO mm_answer_stream(stream_id, session_id, question_event_id, final_answer, created_at) "
                        + "VALUES (?, ?, (SELECT event_id FROM mm_question_event WHERE session_id = ? ORDER BY created_at DESC LIMIT 1), ?, now())",
                streamId,
                sessionId,
                sessionId,
                finalAnswer
        );
    }
}
