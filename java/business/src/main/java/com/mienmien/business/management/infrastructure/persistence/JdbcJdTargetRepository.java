package com.mienmien.business.management.infrastructure.persistence;

import com.mienmien.business.management.domain.model.JdTarget;
import com.mienmien.business.management.domain.repository.JdTargetRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class JdbcJdTargetRepository implements JdTargetRepository {
    private final JdbcTemplate jdbcTemplate;

    public JdbcJdTargetRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void save(JdTarget jd) {
        jdbcTemplate.update(
                "INSERT INTO mm_jd_target(jd_id, space_id, source_type, raw_text, focus_points) VALUES (?, ?, ?, ?, ?)",
                jd.getJdId(),
                jd.getSpaceId(),
                jd.getSourceType(),
                jd.getRawText(),
                jd.getFocusPoints()
        );
    }

    @Override
    public List<JdTarget> findBySpaceIdOrderByCreatedAtDesc(String spaceId) {
        return jdbcTemplate.query(
                "SELECT jd_id, space_id, source_type, raw_text, focus_points FROM mm_jd_target WHERE space_id = ? ORDER BY created_at DESC",
                (rs, rowNum) -> JdTarget.restore(
                        rs.getString("jd_id"),
                        rs.getString("space_id"),
                        rs.getString("source_type"),
                        rs.getString("raw_text"),
                        rs.getString("focus_points")
                ),
                spaceId
        );
    }

    @Override
    public long countBySpaceId(String spaceId) {
        Long n = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM mm_jd_target WHERE space_id = ?",
                Long.class,
                spaceId
        );
        return n == null ? 0L : n;
    }
}
