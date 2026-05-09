package com.mienmien.business.management.infrastructure.persistence;

import com.mienmien.business.management.domain.model.StandardAnswerBank;
import com.mienmien.business.management.domain.repository.StandardAnswerBankRepository;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.Optional;

@Repository
public class JdbcStandardAnswerBankRepository implements StandardAnswerBankRepository {
    private final JdbcTemplate jdbcTemplate;

    public JdbcStandardAnswerBankRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void save(StandardAnswerBank bank) {
        jdbcTemplate.update(
                "INSERT INTO mm_standard_answer_bank(answer_id, space_id, intro, reason, strengths, project, hr, cards_json, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                bank.getAnswerId(),
                bank.getSpaceId(),
                bank.getIntro(),
                bank.getReason(),
                bank.getStrengths(),
                bank.getProject(),
                bank.getHr(),
                bank.getCardsJson(),
                Timestamp.from(bank.getUpdatedAt())
        );
    }

    @Override
    public void update(StandardAnswerBank bank) {
        jdbcTemplate.update(
                "UPDATE mm_standard_answer_bank SET intro = ?, reason = ?, strengths = ?, project = ?, hr = ?, cards_json = ?, updated_at = ? WHERE space_id = ?",
                bank.getIntro(),
                bank.getReason(),
                bank.getStrengths(),
                bank.getProject(),
                bank.getHr(),
                bank.getCardsJson(),
                Timestamp.from(bank.getUpdatedAt()),
                bank.getSpaceId()
        );
    }

    @Override
    public Optional<StandardAnswerBank> findBySpaceId(String spaceId) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                    "SELECT answer_id, space_id, intro, reason, strengths, project, hr, cards_json, updated_at FROM mm_standard_answer_bank WHERE space_id = ?",
                    (rs, rowNum) -> StandardAnswerBank.restore(
                            rs.getString("answer_id"),
                            rs.getString("space_id"),
                            rs.getString("intro"),
                            rs.getString("reason"),
                            rs.getString("strengths"),
                            rs.getString("project"),
                            rs.getString("hr"),
                            rs.getString("cards_json"),
                            rs.getTimestamp("updated_at").toInstant()
                    ),
                    spaceId
            ));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    @Override
    public long countBySpaceId(String spaceId) {
        Long n = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM mm_standard_answer_bank WHERE space_id = ?",
                Long.class,
                spaceId
        );
        return n == null ? 0L : n;
    }
}
