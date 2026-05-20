package com.mienmien.business.management.infrastructure.persistence;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@Order(3)
public class MmUserInterviewerStyleSchemaBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(MmUserInterviewerStyleSchemaBootstrap.class);

    private final JdbcTemplate jdbcTemplate;
    private final boolean enabled;

    public MmUserInterviewerStyleSchemaBootstrap(
            JdbcTemplate jdbcTemplate,
            @Value("${mienmien.business.schema.auto-patch-user-interviewer-style:true}") boolean enabled) {
        this.jdbcTemplate = jdbcTemplate;
        this.enabled = enabled;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) {
            return;
        }
        try {
            jdbcTemplate.execute(
                    """
                            CREATE TABLE IF NOT EXISTS mm_user_interviewer_style (
                              style_id VARCHAR(64) NOT NULL PRIMARY KEY,
                              user_id VARCHAR(64) NOT NULL,
                              title VARCHAR(256) NOT NULL,
                              prompt_body LONGTEXT NOT NULL,
                              created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                              updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                              KEY idx_uis_user (user_id)
                            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                            """);
            log.debug("已确保 mm_user_interviewer_style 存在");
        } catch (Exception e) {
            log.error("mm_user_interviewer_style 自检失败，请执行 scripts/migrate-mm-user-interviewer-style.sql", e);
            throw new IllegalStateException("mm_user_interviewer_style 表创建失败: " + e.getMessage(), e);
        }
    }
}
