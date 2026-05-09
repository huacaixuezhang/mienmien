package com.mienmien.business.management.infrastructure.persistence;

import com.mienmien.business.management.domain.model.Space;
import com.mienmien.business.management.domain.repository.SpaceRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public class JdbcSpaceRepository implements SpaceRepository {
    private final JdbcTemplate jdbcTemplate;

    public JdbcSpaceRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void save(Space space) {
        jdbcTemplate.update(
                "INSERT INTO mm_space(space_id, owner_user_id, name, status, created_at, updated_at, deleted_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
                space.getSpaceId(),
                space.getOwnerUserId(),
                space.getName(),
                space.getStatus(),
                Timestamp.from(space.getCreatedAt()),
                Timestamp.from(space.getUpdatedAt()),
                null
        );
    }

    @Override
    public void update(Space space) {
        jdbcTemplate.update(
                "UPDATE mm_space SET name = ?, status = ?, updated_at = ?, deleted_at = ? WHERE space_id = ?",
                space.getName(),
                space.getStatus(),
                Timestamp.from(space.getUpdatedAt()),
                space.getDeletedAt() == null ? null : Timestamp.from(space.getDeletedAt()),
                space.getSpaceId()
        );
    }

    @Override
    public void deleteById(String spaceId) {
        jdbcTemplate.update("DELETE FROM mm_space WHERE space_id = ?", spaceId);
    }

    @Override
    public Optional<Space> findById(String spaceId) {
        List<Space> list = jdbcTemplate.query(
                "SELECT space_id, owner_user_id, name, status, created_at, updated_at, deleted_at FROM mm_space WHERE space_id = ?",
                (rs, rowNum) -> Space.restore(
                        rs.getString("space_id"),
                        rs.getString("owner_user_id"),
                        rs.getString("name"),
                        rs.getString("status"),
                        rs.getTimestamp("created_at").toInstant(),
                        rs.getTimestamp("updated_at").toInstant(),
                        rs.getTimestamp("deleted_at") == null ? null : rs.getTimestamp("deleted_at").toInstant()
                ),
                spaceId
        );
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    @Override
    public List<Space> findAllActiveOrderByCreatedAtDesc() {
        return jdbcTemplate.query(
                "SELECT space_id, owner_user_id, name, status, created_at, updated_at, deleted_at FROM mm_space WHERE status = 'ACTIVE' ORDER BY created_at DESC",
                (rs, rowNum) -> Space.restore(
                        rs.getString("space_id"),
                        rs.getString("owner_user_id"),
                        rs.getString("name"),
                        rs.getString("status"),
                        rs.getTimestamp("created_at").toInstant(),
                        rs.getTimestamp("updated_at").toInstant(),
                        rs.getTimestamp("deleted_at") == null ? null : rs.getTimestamp("deleted_at").toInstant()
                )
        );
    }

    @Override
    public List<Space> findAllRecycledOrderByDeletedAtDesc() {
        return jdbcTemplate.query(
                "SELECT space_id, owner_user_id, name, status, created_at, updated_at, deleted_at FROM mm_space WHERE status = 'RECYCLED' ORDER BY deleted_at DESC",
                (rs, rowNum) -> Space.restore(
                        rs.getString("space_id"),
                        rs.getString("owner_user_id"),
                        rs.getString("name"),
                        rs.getString("status"),
                        rs.getTimestamp("created_at").toInstant(),
                        rs.getTimestamp("updated_at").toInstant(),
                        rs.getTimestamp("deleted_at") == null ? null : rs.getTimestamp("deleted_at").toInstant()
                )
        );
    }

    @Override
    public List<Space> findActiveByOwnerUserIdOrderByCreatedAtDesc(String ownerUserId) {
        return jdbcTemplate.query(
                "SELECT space_id, owner_user_id, name, status, created_at, updated_at, deleted_at FROM mm_space "
                        + "WHERE status = 'ACTIVE' AND owner_user_id = ? ORDER BY created_at DESC",
                (rs, rowNum) -> Space.restore(
                        rs.getString("space_id"),
                        rs.getString("owner_user_id"),
                        rs.getString("name"),
                        rs.getString("status"),
                        rs.getTimestamp("created_at").toInstant(),
                        rs.getTimestamp("updated_at").toInstant(),
                        rs.getTimestamp("deleted_at") == null ? null : rs.getTimestamp("deleted_at").toInstant()
                ),
                ownerUserId
        );
    }

    @Override
    public List<Space> findRecycledByOwnerUserIdOrderByDeletedAtDesc(String ownerUserId) {
        return jdbcTemplate.query(
                "SELECT space_id, owner_user_id, name, status, created_at, updated_at, deleted_at FROM mm_space "
                        + "WHERE status = 'RECYCLED' AND owner_user_id = ? ORDER BY deleted_at DESC",
                (rs, rowNum) -> Space.restore(
                        rs.getString("space_id"),
                        rs.getString("owner_user_id"),
                        rs.getString("name"),
                        rs.getString("status"),
                        rs.getTimestamp("created_at").toInstant(),
                        rs.getTimestamp("updated_at").toInstant(),
                        rs.getTimestamp("deleted_at") == null ? null : rs.getTimestamp("deleted_at").toInstant()
                ),
                ownerUserId
        );
    }
}
