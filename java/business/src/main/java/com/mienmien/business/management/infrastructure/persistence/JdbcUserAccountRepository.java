package com.mienmien.business.management.infrastructure.persistence;

import com.mienmien.business.management.domain.model.UserAccount;
import com.mienmien.business.management.domain.repository.UserAccountRepository;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

@Repository
public class JdbcUserAccountRepository implements UserAccountRepository {
    private final JdbcTemplate jdbcTemplate;

    public JdbcUserAccountRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void save(UserAccount userAccount) {
        jdbcTemplate.update(
                "INSERT INTO mm_user_account(user_id, phone, password, created_at, updated_at) VALUES (?, ?, ?, ?, ?)",
                userAccount.getUserId(),
                userAccount.getPhone(),
                userAccount.getPasswordDigest(),
                Timestamp.from(userAccount.getCreatedAt()),
                Timestamp.from(userAccount.getUpdatedAt())
        );
    }

    @Override
    public Optional<UserAccount> findByPhone(String phone) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                    "SELECT user_id, phone, password, created_at, updated_at FROM mm_user_account WHERE phone = ?",
                    (rs, rowNum) -> UserAccount.restore(
                            rs.getString("user_id"),
                            rs.getString("phone"),
                            rs.getString("password"),
                            rs.getTimestamp("created_at").toInstant(),
                            rs.getTimestamp("updated_at").toInstant()
                    ),
                    phone == null ? "" : phone.trim()
            ));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
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
