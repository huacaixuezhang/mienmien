package com.mienmien.business.management.infrastructure.persistence;

import com.mienmien.business.management.domain.model.Resume;
import com.mienmien.business.management.domain.repository.ResumeRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;

@Repository
public class JdbcResumeRepository implements ResumeRepository {
    private final JdbcTemplate jdbcTemplate;

    public JdbcResumeRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void save(Resume resume) {
        jdbcTemplate.update(
                "INSERT INTO mm_resume(resume_id, space_id, version, content, is_active, updated_at) VALUES (?, ?, ?, ?, ?, ?)",
                resume.getResumeId(),
                resume.getSpaceId(),
                resume.getVersion(),
                resume.getContent(),
                resume.isActive(),
                Timestamp.from(resume.getUpdatedAt())
        );
    }

    @Override
    public List<Resume> findBySpaceIdOrderByVersionDesc(String spaceId) {
        return jdbcTemplate.query(
                "SELECT resume_id, space_id, version, content, is_active, updated_at FROM mm_resume WHERE space_id = ? ORDER BY version DESC",
                (rs, rowNum) -> Resume.restore(
                        rs.getString("resume_id"),
                        rs.getString("space_id"),
                        rs.getInt("version"),
                        rs.getString("content"),
                        rs.getBoolean("is_active"),
                        rs.getTimestamp("updated_at").toInstant()
                ),
                spaceId
        );
    }

    @Override
    public boolean existsBySpaceIdAndVersion(String spaceId, int version) {
        Long n = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM mm_resume WHERE space_id = ? AND version = ?",
                Long.class,
                spaceId,
                version
        );
        return n != null && n > 0;
    }

    @Override
    public long countBySpaceId(String spaceId) {
        Long n = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM mm_resume WHERE space_id = ?",
                Long.class,
                spaceId
        );
        return n == null ? 0L : n;
    }
}
