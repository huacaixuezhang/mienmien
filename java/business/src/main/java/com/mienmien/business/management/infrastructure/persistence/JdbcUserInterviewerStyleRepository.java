package com.mienmien.business.management.infrastructure.persistence;

import com.mienmien.business.management.domain.model.UserInterviewerStyle;
import com.mienmien.business.management.domain.repository.UserInterviewerStyleRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public class JdbcUserInterviewerStyleRepository implements UserInterviewerStyleRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcUserInterviewerStyleRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private static final RowMapper<UserInterviewerStyle> MAPPER =
            (rs, rowNum) ->
                    UserInterviewerStyle.restore(
                            rs.getString("style_id"),
                            rs.getString("user_id"),
                            rs.getString("title"),
                            rs.getString("prompt_body"),
                            rs.getTimestamp("created_at").toInstant(),
                            rs.getTimestamp("updated_at").toInstant());

    @Override
    public void insert(UserInterviewerStyle style) {
        jdbcTemplate.update(
                """
                        INSERT INTO mm_user_interviewer_style
                        (style_id, user_id, title, prompt_body, created_at, updated_at)
                        VALUES (?,?,?,?,?,?)
                        """,
                style.getStyleId(),
                style.getUserId(),
                style.getTitle(),
                style.getPromptBody(),
                Timestamp.from(style.getCreatedAt()),
                Timestamp.from(style.getUpdatedAt()));
    }

    @Override
    public void update(UserInterviewerStyle style) {
        jdbcTemplate.update(
                """
                        UPDATE mm_user_interviewer_style
                        SET title=?, prompt_body=?, updated_at=?
                        WHERE style_id=? AND user_id=?
                        """,
                style.getTitle(),
                style.getPromptBody(),
                Timestamp.from(style.getUpdatedAt()),
                style.getStyleId(),
                style.getUserId());
    }

    @Override
    public void deleteByIdAndOwnerUserId(String styleId, String ownerUserId) {
        jdbcTemplate.update(
                "DELETE FROM mm_user_interviewer_style WHERE style_id = ? AND user_id = ?", styleId, ownerUserId);
    }

    @Override
    public Optional<UserInterviewerStyle> findByIdAndOwnerUserId(String styleId, String ownerUserId) {
        List<UserInterviewerStyle> list =
                jdbcTemplate.query(
                        "SELECT style_id, user_id, title, prompt_body, created_at, updated_at "
                                + "FROM mm_user_interviewer_style WHERE style_id = ? AND user_id = ?",
                        MAPPER,
                        styleId,
                        ownerUserId);
        return list.stream().findFirst();
    }

    @Override
    public List<UserInterviewerStyle> findByOwnerUserIdOrderByUpdatedAtDesc(String ownerUserId) {
        return jdbcTemplate.query(
                """
                        SELECT style_id, user_id, title, prompt_body, created_at, updated_at
                        FROM mm_user_interviewer_style
                        WHERE user_id = ?
                        ORDER BY updated_at DESC
                        """,
                MAPPER,
                ownerUserId);
    }
}
