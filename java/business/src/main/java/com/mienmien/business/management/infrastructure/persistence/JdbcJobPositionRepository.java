package com.mienmien.business.management.infrastructure.persistence;

import com.mienmien.business.management.domain.model.JobPosition;
import com.mienmien.business.management.domain.repository.JobPositionRepository;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

@Repository
public class JdbcJobPositionRepository implements JobPositionRepository {
    private final JdbcTemplate jdbcTemplate;

    public JdbcJobPositionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void save(JobPosition p) {
        jdbcTemplate.update(
                "INSERT INTO mm_job_position(position_id, space_id, title, company, location, base_range, status, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                p.getPositionId(),
                p.getSpaceId(),
                p.getTitle(),
                p.getCompany(),
                p.getLocation(),
                p.getBaseRange(),
                p.getStatus(),
                Timestamp.from(p.getCreatedAt()),
                Timestamp.from(p.getUpdatedAt())
        );
    }

    @Override
    public void update(JobPosition p) {
        jdbcTemplate.update(
                "UPDATE mm_job_position SET title = ?, company = ?, location = ?, base_range = ?, status = ?, updated_at = ? WHERE position_id = ?",
                p.getTitle(),
                p.getCompany(),
                p.getLocation(),
                p.getBaseRange(),
                p.getStatus(),
                Timestamp.from(p.getUpdatedAt()),
                p.getPositionId()
        );
    }

    @Override
    public Optional<JobPosition> findById(String positionId) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                    "SELECT position_id, space_id, title, company, location, base_range, status, created_at, updated_at "
                            + "FROM mm_job_position WHERE position_id = ?",
                    (rs, rowNum) -> JobPosition.restore(
                            rs.getString("position_id"),
                            rs.getString("space_id"),
                            rs.getString("title"),
                            rs.getString("company"),
                            rs.getString("location"),
                            rs.getString("base_range"),
                            rs.getString("status"),
                            rs.getTimestamp("created_at").toInstant(),
                            rs.getTimestamp("updated_at").toInstant()
                    ),
                    positionId
            ));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    @Override
    public List<JobPosition> findBySpaceIdOrderByCreatedAtDesc(String spaceId) {
        return jdbcTemplate.query(
                "SELECT position_id, space_id, title, company, location, base_range, status, created_at, updated_at "
                        + "FROM mm_job_position WHERE space_id = ? ORDER BY created_at DESC",
                (rs, rowNum) -> JobPosition.restore(
                        rs.getString("position_id"),
                        rs.getString("space_id"),
                        rs.getString("title"),
                        rs.getString("company"),
                        rs.getString("location"),
                        rs.getString("base_range"),
                        rs.getString("status"),
                        rs.getTimestamp("created_at").toInstant(),
                        rs.getTimestamp("updated_at").toInstant()
                ),
                spaceId
        );
    }

    @Override
    public long countBySpaceId(String spaceId) {
        Long n = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM mm_job_position WHERE space_id = ? AND status = 'ACTIVE'",
                Long.class,
                spaceId
        );
        return n == null ? 0L : n;
    }
}
