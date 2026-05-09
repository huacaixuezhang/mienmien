package com.mienmien.business.management.infrastructure.persistence;

import com.mienmien.business.management.domain.model.InterviewRecord;
import com.mienmien.business.management.domain.repository.InterviewRecordRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class JdbcInterviewRecordRepository implements InterviewRecordRepository {
    private final JdbcTemplate jdbcTemplate;

    public JdbcInterviewRecordRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void save(InterviewRecord record) {
        jdbcTemplate.update(
                "INSERT INTO mm_interview_record(record_id, space_id, type, round, interview_type, score, result, summary) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                record.getRecordId(),
                record.getSpaceId(),
                record.getCategory(),
                record.getRound(),
                record.getInterviewType(),
                record.getScore(),
                record.getResult(),
                record.getSummary()
        );
    }

    @Override
    public List<InterviewRecord> findBySpaceIdOrderByCreatedAtDesc(String spaceId) {
        return jdbcTemplate.query(
                "SELECT record_id, space_id, type, round, interview_type, score, result, summary FROM mm_interview_record WHERE space_id = ? ORDER BY created_at DESC",
                (rs, rowNum) -> InterviewRecord.restore(
                        rs.getString("record_id"),
                        rs.getString("space_id"),
                        rs.getString("type"),
                        rs.getInt("round"),
                        rs.getString("interview_type"),
                        rs.getInt("score"),
                        rs.getString("result"),
                        rs.getString("summary")
                ),
                spaceId
        );
    }

    @Override
    public long countBySpaceId(String spaceId) {
        Long n = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM mm_interview_record WHERE space_id = ?",
                Long.class,
                spaceId
        );
        return n == null ? 0L : n;
    }
}
