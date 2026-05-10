package com.mienmien.business.management.infrastructure.persistence;

import com.mienmien.business.management.domain.model.AiModelConfig;
import com.mienmien.business.management.domain.repository.AiModelConfigRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public class JdbcAiModelConfigRepository implements AiModelConfigRepository {
    private static final Logger log = LoggerFactory.getLogger(JdbcAiModelConfigRepository.class);
    private final JdbcTemplate jdbcTemplate;

    public JdbcAiModelConfigRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private static final RowMapper<AiModelConfig> MAPPER = (rs, rowNum) ->
            AiModelConfig.restore(
                    rs.getString("config_id"),
                    rs.getString("owner_user_id"),
                    rs.getString("provider"),
                    rs.getString("base_url"),
                    rs.getString("api_key"),
                    rs.getString("model_name"),
                    rs.getTimestamp("updated_at").toInstant());

    /**
     * 将历史「按空间」表结构迁移为「按用户」，与 scripts/migrate-mm-ai-model-config-user-scope.sql 语义一致。
     */
    @PostConstruct
    void alignLegacyAiModelConfigUserScope() {
        try {
            if (!aiModelConfigTableExists()) {
                return;
            }
            if (columnExists("owner_user_id")) {
                return;
            }
            if (!columnExists("space_id")) {
                log.warn("mm_ai_model_config 缺少 owner_user_id 与 space_id，跳过自动迁移");
                return;
            }
            log.info("对齐 mm_ai_model_config：由 space_id 迁移为 owner_user_id（用户级共享配置）");
            jdbcTemplate.execute(
                    "ALTER TABLE mm_ai_model_config ADD COLUMN owner_user_id VARCHAR(64) NULL AFTER config_id");
            jdbcTemplate.update(
                    """
                            UPDATE mm_ai_model_config c
                            INNER JOIN mm_space s ON s.space_id = c.space_id
                            SET c.owner_user_id = s.owner_user_id
                            WHERE c.owner_user_id IS NULL
                            """);
            jdbcTemplate.update("DELETE FROM mm_ai_model_config WHERE owner_user_id IS NULL");
            jdbcTemplate.update(
                    """
                            DELETE c FROM mm_ai_model_config c
                            JOIN (
                                SELECT owner_user_id, MIN(config_id) AS keep_id
                                FROM mm_ai_model_config
                                GROUP BY owner_user_id
                            ) k ON c.owner_user_id = k.owner_user_id AND c.config_id <> k.keep_id
                            """);
            jdbcTemplate.execute("ALTER TABLE mm_ai_model_config DROP INDEX uk_ai_model_config_space");
            jdbcTemplate.execute("ALTER TABLE mm_ai_model_config DROP COLUMN space_id");
            jdbcTemplate.execute("ALTER TABLE mm_ai_model_config MODIFY owner_user_id VARCHAR(64) NOT NULL");
            jdbcTemplate.execute("ALTER TABLE mm_ai_model_config ADD UNIQUE KEY uk_ai_model_config_user (owner_user_id)");
        } catch (Exception e) {
            log.warn("mm_ai_model_config 用户级迁移未完全执行（可手工执行 scripts/migrate-mm-ai-model-config-user-scope.sql）: {}", e.getMessage());
        }
    }

    private boolean aiModelConfigTableExists() {
        Integer n =
                jdbcTemplate.queryForObject(
                        """
                                SELECT COUNT(*) FROM information_schema.TABLES
                                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mm_ai_model_config'
                                """,
                        Integer.class);
        return n != null && n > 0;
    }

    private boolean columnExists(String columnName) {
        Integer n =
                jdbcTemplate.queryForObject(
                        """
                                SELECT COUNT(*) FROM information_schema.COLUMNS
                                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mm_ai_model_config' AND COLUMN_NAME = ?
                                """,
                        Integer.class,
                        columnName);
        return n != null && n > 0;
    }

    @Override
    public Optional<AiModelConfig> findByOwnerUserId(String ownerUserId) {
        List<AiModelConfig> list =
                jdbcTemplate.query(
                        """
                                SELECT config_id, owner_user_id, provider, base_url, api_key, model_name, updated_at
                                FROM mm_ai_model_config
                                WHERE owner_user_id = ?
                                """,
                        MAPPER,
                        ownerUserId);
        return list.stream().findFirst();
    }

    @Override
    public void save(AiModelConfig config) {
        jdbcTemplate.update(
                """
                        INSERT INTO mm_ai_model_config (config_id, owner_user_id, provider, base_url, api_key, model_name, updated_at)
                        VALUES (?,?,?,?,?,?,?)
                        """,
                config.getConfigId(),
                config.getOwnerUserId(),
                config.getProvider(),
                config.getBaseUrl(),
                config.getApiKey(),
                config.getModelName(),
                Timestamp.from(config.getUpdatedAt()));
    }

    @Override
    public void update(AiModelConfig config) {
        jdbcTemplate.update(
                """
                        UPDATE mm_ai_model_config
                        SET provider=?, base_url=?, api_key=?, model_name=?, updated_at=?
                        WHERE owner_user_id=?
                        """,
                config.getProvider(),
                config.getBaseUrl(),
                config.getApiKey(),
                config.getModelName(),
                Timestamp.from(config.getUpdatedAt()),
                config.getOwnerUserId());
    }
}
