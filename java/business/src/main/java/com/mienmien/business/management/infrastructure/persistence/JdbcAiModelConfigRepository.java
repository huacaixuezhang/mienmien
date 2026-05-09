package com.mienmien.business.management.infrastructure.persistence;

import com.mienmien.business.management.domain.model.AiModelConfig;
import com.mienmien.business.management.domain.repository.AiModelConfigRepository;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.Optional;

@Repository
public class JdbcAiModelConfigRepository implements AiModelConfigRepository {
    private final JdbcTemplate jdbcTemplate;

    public JdbcAiModelConfigRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<AiModelConfig> findBySpaceId(String spaceId) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                    "SELECT config_id, space_id, provider, base_url, api_key, model_name, updated_at "
                            + "FROM mm_ai_model_config WHERE space_id = ?",
                    (rs, rowNum) -> AiModelConfig.restore(
                            rs.getString("config_id"),
                            rs.getString("space_id"),
                            rs.getString("provider"),
                            rs.getString("base_url"),
                            rs.getString("api_key"),
                            rs.getString("model_name"),
                            rs.getTimestamp("updated_at").toInstant()
                    ),
                    spaceId
            ));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    @Override
    public void save(AiModelConfig config) {
        jdbcTemplate.update(
                "INSERT INTO mm_ai_model_config(config_id, space_id, provider, base_url, api_key, model_name, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                config.getConfigId(),
                config.getSpaceId(),
                config.getProvider(),
                config.getBaseUrl(),
                config.getApiKey(),
                config.getModelName(),
                Timestamp.from(config.getUpdatedAt())
        );
    }

    @Override
    public void update(AiModelConfig config) {
        jdbcTemplate.update(
                "UPDATE mm_ai_model_config SET provider = ?, base_url = ?, api_key = ?, model_name = ?, updated_at = ? "
                        + "WHERE space_id = ?",
                config.getProvider(),
                config.getBaseUrl(),
                config.getApiKey(),
                config.getModelName(),
                Timestamp.from(config.getUpdatedAt()),
                config.getSpaceId()
        );
    }
}
