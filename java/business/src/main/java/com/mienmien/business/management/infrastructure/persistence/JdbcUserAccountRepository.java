package com.mienmien.business.management.infrastructure.persistence;

import com.mienmien.business.management.domain.model.UserAccount;
import com.mienmien.business.management.domain.repository.UserAccountRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public class JdbcUserAccountRepository implements UserAccountRepository {
    private final JdbcTemplate jdbcTemplate;

    public JdbcUserAccountRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private static final RowMapper<UserAccount> MAPPER = (rs, rowNum) -> UserAccount.restore(
            rs.getString("user_id"),
            rs.getString("phone"),
            rs.getString("password"),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("updated_at").toInstant()
    );

    @Override
    public void save(UserAccount userAccount) {
        jdbcTemplate.update(
                """
                        INSERT INTO mm_user_account (user_id, phone, password, created_at, updated_at)
                        VALUES (?,?,?,?,?)
                        """,
                userAccount.getUserId(),
                userAccount.getPhone(),
                userAccount.getPasswordDigest(),
                Timestamp.from(userAccount.getCreatedAt()),
                Timestamp.from(userAccount.getUpdatedAt())
        );
    }

    @Override
    public Optional<UserAccount> findByPhone(String phone) {
        List<UserAccount> list = jdbcTemplate.query(
                "SELECT user_id, phone, password, created_at, updated_at FROM mm_user_account WHERE phone = ? LIMIT 1",
                MAPPER,
                phone
        );
        return list.stream().findFirst();
    }

    @Override
    public Optional<UserAccount> findByUserId(String userId) {
        List<UserAccount> list = jdbcTemplate.query(
                "SELECT user_id, phone, password, created_at, updated_at FROM mm_user_account WHERE user_id = ? LIMIT 1",
                MAPPER,
                userId
        );
        return list.stream().findFirst();
    }

    @Override
    public void updatePasswordDigest(String userId, String passwordDigest) {
        jdbcTemplate.update(
                "UPDATE mm_user_account SET password = ?, updated_at = ? WHERE user_id = ?",
                passwordDigest,
                Timestamp.from(Instant.now()),
                userId
        );
    }
}
