package com.mienmien.business.management.infrastructure.persistence;

import com.mienmien.business.management.domain.model.UserInterviewerRole;
import com.mienmien.business.management.domain.repository.UserInterviewerRoleRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcUserInterviewerRoleRepository implements UserInterviewerRoleRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcUserInterviewerRoleRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private static final RowMapper<UserInterviewerRole> MAPPER =
            (rs, rowNum) ->
                    UserInterviewerRole.restore(
                            rs.getString("role_id"),
                            rs.getString("user_id"),
                            rs.getString("role_code"),
                            rs.getString("role_name"),
                            rs.getString("interview_content"),
                            rs.getString("focus_points"),
                            rs.getString("evaluation_hint") == null ? "" : rs.getString("evaluation_hint"),
                            rs.getTimestamp("created_at").toInstant(),
                            rs.getTimestamp("updated_at").toInstant());

    @Override
    public void insert(UserInterviewerRole role) {
        jdbcTemplate.update(
                """
                        INSERT INTO mm_user_interviewer_role
                        (role_id, user_id, role_code, role_name, interview_content, focus_points, evaluation_hint, created_at, updated_at)
                        VALUES (?,?,?,?,?,?,?,?,?)
                        """,
                role.getRoleId(),
                role.getUserId(),
                role.getRoleCode(),
                role.getRoleName(),
                role.getInterviewContent(),
                role.getFocusPoints(),
                role.getEvaluationHint(),
                Timestamp.from(role.getCreatedAt()),
                Timestamp.from(role.getUpdatedAt()));
    }

    @Override
    public void update(UserInterviewerRole role) {
        jdbcTemplate.update(
                """
                        UPDATE mm_user_interviewer_role
                        SET role_code=?, role_name=?, interview_content=?, focus_points=?, evaluation_hint=?, updated_at=?
                        WHERE role_id=? AND user_id=?
                        """,
                role.getRoleCode(),
                role.getRoleName(),
                role.getInterviewContent(),
                role.getFocusPoints(),
                role.getEvaluationHint(),
                Timestamp.from(role.getUpdatedAt()),
                role.getRoleId(),
                role.getUserId());
    }

    @Override
    public void deleteByIdAndOwnerUserId(String roleId, String ownerUserId) {
        jdbcTemplate.update(
                "DELETE FROM mm_user_interviewer_role WHERE role_id = ? AND user_id = ?", roleId, ownerUserId);
    }

    @Override
    public Optional<UserInterviewerRole> findByIdAndOwnerUserId(String roleId, String ownerUserId) {
        List<UserInterviewerRole> list =
                jdbcTemplate.query(
                        """
                                SELECT role_id, user_id, role_code, role_name, interview_content, focus_points, evaluation_hint, created_at, updated_at
                                FROM mm_user_interviewer_role WHERE role_id = ? AND user_id = ?
                                """,
                        MAPPER,
                        roleId,
                        ownerUserId);
        return list.stream().findFirst();
    }

    @Override
    public List<UserInterviewerRole> findByOwnerUserIdOrderByUpdatedAtDesc(String ownerUserId) {
        return jdbcTemplate.query(
                """
                        SELECT role_id, user_id, role_code, role_name, interview_content, focus_points, evaluation_hint, created_at, updated_at
                        FROM mm_user_interviewer_role
                        WHERE user_id = ?
                        ORDER BY updated_at DESC
                        """,
                MAPPER,
                ownerUserId);
    }

    @Override
    public Optional<UserInterviewerRole> findByOwnerUserIdAndRoleCodeIgnoreCase(String ownerUserId, String roleCode) {
        List<UserInterviewerRole> list =
                jdbcTemplate.query(
                        """
                                SELECT role_id, user_id, role_code, role_name, interview_content, focus_points, evaluation_hint, created_at, updated_at
                                FROM mm_user_interviewer_role
                                WHERE user_id = ? AND LOWER(role_code) = LOWER(?)
                                LIMIT 1
                                """,
                        MAPPER,
                        ownerUserId,
                        roleCode);
        return list.stream().findFirst();
    }
}
