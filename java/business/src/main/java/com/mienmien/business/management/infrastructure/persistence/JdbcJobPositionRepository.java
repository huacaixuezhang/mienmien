package com.mienmien.business.management.infrastructure.persistence;

import com.mienmien.business.management.domain.model.JobPosition;
import com.mienmien.business.management.domain.repository.JobPositionRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

@Repository
public class JdbcJobPositionRepository implements JobPositionRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcJobPositionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private static final RowMapper<JobPosition> MAPPER =
            (rs, rowNum) ->
                    JobPosition.restore(
                            rs.getString("position_id"),
                            rs.getString("user_id"),
                            rs.getString("title"),
                            rs.getString("company"),
                            rs.getString("location"),
                            rs.getString("base_range"),
                            rs.getString("status"),
                            rs.getTimestamp("created_at").toInstant(),
                            rs.getTimestamp("updated_at").toInstant());

    @Override
    public void insert(JobPosition position, List<String> linkedSpaceIds) {
        List<String> sorted = new ArrayList<>(normalizeSpaceIds(linkedSpaceIds));
        Collections.sort(sorted);
        String primarySpace = sorted.isEmpty() ? null : sorted.get(0);
        jdbcTemplate.update(
                """
                        INSERT INTO mm_job_position
                        (position_id, user_id, space_id, title, company, location, base_range, status, created_at, updated_at)
                        VALUES (?,?,?,?,?,?,?,?,?,?)
                        """,
                position.getPositionId(),
                position.getUserId(),
                primarySpace,
                position.getTitle(),
                position.getCompany(),
                position.getLocation(),
                position.getBaseRange(),
                position.getStatus(),
                Timestamp.from(position.getCreatedAt()),
                Timestamp.from(position.getUpdatedAt()));
        for (String sid : sorted) {
            jdbcTemplate.update(
                    "INSERT IGNORE INTO mm_job_position_space (position_id, space_id) VALUES (?,?)",
                    position.getPositionId(),
                    sid);
        }
    }

    @Override
    public void update(JobPosition position) {
        jdbcTemplate.update(
                """
                        UPDATE mm_job_position
                        SET title=?, company=?, location=?, base_range=?, status=?, updated_at=?
                        WHERE position_id=?
                        """,
                position.getTitle(),
                position.getCompany(),
                position.getLocation(),
                position.getBaseRange(),
                position.getStatus(),
                Timestamp.from(position.getUpdatedAt()),
                position.getPositionId());
    }

    @Override
    public void deleteById(String positionId) {
        jdbcTemplate.update("DELETE FROM mm_job_position WHERE position_id = ?", positionId);
    }

    @Override
    public Optional<JobPosition> findById(String positionId) {
        List<JobPosition> list =
                jdbcTemplate.query(
                        "SELECT position_id, user_id, title, company, location, base_range, status, created_at, updated_at "
                                + "FROM mm_job_position WHERE position_id = ?",
                        MAPPER,
                        positionId);
        return list.stream().findFirst();
    }

    @Override
    public List<JobPosition> findByOwnerUserIdOrderByUpdatedAtDesc(String ownerUserId) {
        return jdbcTemplate.query(
                """
                        SELECT position_id, user_id, title, company, location, base_range, status, created_at, updated_at
                        FROM mm_job_position
                        WHERE user_id = ?
                        ORDER BY updated_at DESC
                        """,
                MAPPER,
                ownerUserId);
    }

    @Override
    public List<JobPosition> findByLinkedSpaceIdOrderByUpdatedAtDesc(String spaceId) {
        return jdbcTemplate.query(
                """
                        SELECT DISTINCT jp.position_id, jp.user_id, jp.title, jp.company, jp.location, jp.base_range,
                               jp.status, jp.created_at, jp.updated_at
                        FROM mm_job_position jp
                        INNER JOIN mm_job_position_space jps ON jp.position_id = jps.position_id
                        WHERE jps.space_id = ?
                        ORDER BY jp.updated_at DESC
                        """,
                MAPPER,
                spaceId);
    }

    @Override
    public List<String> findSpaceIdsByPositionId(String positionId) {
        List<String> fromJunction =
                jdbcTemplate.query(
                        "SELECT space_id FROM mm_job_position_space WHERE position_id = ? ORDER BY space_id",
                        (rs, i) -> rs.getString(1),
                        positionId);
        if (!fromJunction.isEmpty()) {
            return fromJunction;
        }
        String legacy =
                jdbcTemplate.query(
                        "SELECT space_id FROM mm_job_position WHERE position_id = ?",
                        rs -> {
                            if (!rs.next()) {
                                return null;
                            }
                            return rs.getString(1);
                        },
                        positionId);
        if (legacy != null && !legacy.isBlank()) {
            return List.of(legacy);
        }
        return List.of();
    }

    @Override
    public void addSpaceLink(String positionId, String spaceId) {
        jdbcTemplate.update(
                "INSERT IGNORE INTO mm_job_position_space (position_id, space_id) VALUES (?,?)", positionId, spaceId);
        syncPrimarySpaceColumn(positionId);
    }

    @Override
    public void removeSpaceLink(String positionId, String spaceId) {
        jdbcTemplate.update(
                "DELETE FROM mm_job_position_space WHERE position_id = ? AND space_id = ?", positionId, spaceId);
        syncPrimarySpaceColumn(positionId);
    }

    @Override
    public void syncPrimarySpaceColumn(String positionId) {
        List<String> ids = findSpaceIdsByPositionId(positionId);
        String primary = ids.isEmpty() ? null : ids.get(0);
        jdbcTemplate.update("UPDATE mm_job_position SET space_id = ? WHERE position_id = ?", primary, positionId);
    }

    private static List<String> normalizeSpaceIds(List<String> linkedSpaceIds) {
        if (linkedSpaceIds == null || linkedSpaceIds.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> set = new LinkedHashSet<>();
        for (String s : linkedSpaceIds) {
            if (s == null) {
                continue;
            }
            String t = s.trim();
            if (!t.isEmpty()) {
                set.add(t);
            }
        }
        return new ArrayList<>(set);
    }
}
