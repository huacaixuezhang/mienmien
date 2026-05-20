package com.mienmien.business.management.infrastructure.persistence;

import com.mienmien.business.management.domain.model.InterviewRecord;
import com.mienmien.business.management.domain.model.InterviewRecordWithMeta;
import com.mienmien.business.management.domain.repository.InterviewRecordRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public class JdbcInterviewRecordRepository implements InterviewRecordRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcInterviewRecordRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private static final RowMapper<InterviewRecordWithMeta> ROW_MAPPER =
            (rs, rowNum) -> {
                InterviewRecord record =
                        InterviewRecord.restore(
                                rs.getString("record_id"),
                                rs.getString("space_id"),
                                rs.getString("type"),
                                rs.getInt("round"),
                                rs.getString("interview_type"),
                                rs.getInt("score"),
                                rs.getString("result"),
                                rs.getString("summary"));
                Instant createdAt = rs.getTimestamp("created_at").toInstant();
                String positionId = rs.getString("bound_position_id");
                if (positionId != null && positionId.isBlank()) {
                    positionId = null;
                }
                return new InterviewRecordWithMeta(record, createdAt, positionId);
            };

    @Override
    public void insert(InterviewRecord record, Instant createdAt, String boundPositionIdOrNull) {
        jdbcTemplate.update(
                """
                        INSERT INTO mm_interview_record
                        (record_id, space_id, type, round, interview_type, score, result, summary, created_at)
                        VALUES (?,?,?,?,?,?,?,?,?)
                        """,
                record.getRecordId(),
                record.getSpaceId(),
                record.getCategory(),
                record.getRound(),
                record.getInterviewType(),
                record.getScore(),
                record.getResult(),
                record.getSummary(),
                Timestamp.from(createdAt));
        syncBinding(record.getRecordId(), boundPositionIdOrNull);
    }

    @Override
    public void update(InterviewRecord record, String boundPositionIdOrNull) {
        jdbcTemplate.update(
                """
                        UPDATE mm_interview_record
                        SET round=?, interview_type=?, score=?, result=?, summary=?
                        WHERE record_id=? AND space_id=?
                        """,
                record.getRound(),
                record.getInterviewType(),
                record.getScore(),
                record.getResult(),
                record.getSummary(),
                record.getRecordId(),
                record.getSpaceId());
        syncBinding(record.getRecordId(), boundPositionIdOrNull);
    }

    @Override
    public List<InterviewRecordWithMeta> findBySpaceIdOrderByCreatedAtDesc(String spaceId) {
        return jdbcTemplate.query(
                """
                        SELECT ir.record_id, ir.space_id, ir.type, ir.round, ir.interview_type, ir.score, ir.result,
                               ir.summary, ir.created_at, irj.position_id AS bound_position_id
                        FROM mm_interview_record ir
                        LEFT JOIN mm_interview_record_job irj ON ir.record_id = irj.record_id
                        WHERE ir.space_id = ?
                        ORDER BY ir.created_at DESC
                        """,
                ROW_MAPPER,
                spaceId);
    }

    @Override
    public Optional<InterviewRecordWithMeta> findByRecordIdAndSpaceId(String recordId, String spaceId) {
        List<InterviewRecordWithMeta> list =
                jdbcTemplate.query(
                        """
                                SELECT ir.record_id, ir.space_id, ir.type, ir.round, ir.interview_type, ir.score, ir.result,
                                       ir.summary, ir.created_at, irj.position_id AS bound_position_id
                                FROM mm_interview_record ir
                                LEFT JOIN mm_interview_record_job irj ON ir.record_id = irj.record_id
                                WHERE ir.record_id = ? AND ir.space_id = ?
                                """,
                        ROW_MAPPER,
                        recordId,
                        spaceId);
        return list.stream().findFirst();
    }

    @Override
    public long countBySpaceId(String spaceId) {
        Long n =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM mm_interview_record WHERE space_id = ?", Long.class, spaceId);
        return n == null ? 0L : n;
    }

    private void syncBinding(String recordId, String boundPositionIdOrNull) {
        jdbcTemplate.update("DELETE FROM mm_interview_record_job WHERE record_id = ?", recordId);
        if (boundPositionIdOrNull == null || boundPositionIdOrNull.isBlank()) {
            return;
        }
        jdbcTemplate.update(
                "INSERT INTO mm_interview_record_job (record_id, position_id) VALUES (?,?)",
                recordId,
                boundPositionIdOrNull.trim());
    }
}
