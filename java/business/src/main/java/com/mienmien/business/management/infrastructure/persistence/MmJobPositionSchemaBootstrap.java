package com.mienmien.business.management.infrastructure.persistence;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 启动时自检岗位表：补齐 {@code user_id}、多空间表、以及将 {@code base_range} 扩为 {@code LONGTEXT}（避免长 JD JSON 触发 Data truncation）。
 * 等价脚本见 {@code scripts/migrate-mm-job-position-jdbc-persist.sql} 等。
 */
@Component
@Order(1)
public class MmJobPositionSchemaBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(MmJobPositionSchemaBootstrap.class);

    private final JdbcTemplate jdbcTemplate;
    private final boolean enabled;

    public MmJobPositionSchemaBootstrap(
            JdbcTemplate jdbcTemplate,
            @Value("${mienmien.business.schema.auto-patch-job-position:true}") boolean enabled) {
        this.jdbcTemplate = jdbcTemplate;
        this.enabled = enabled;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) {
            log.debug("已禁用 mienmien.business.schema.auto-patch-job-position，跳过岗位表结构自检");
            return;
        }
        try {
            if (!tableExists("mm_job_position")) {
                log.warn("库中不存在表 mm_job_position，跳过结构补丁（请先执行 scripts/seed-mienmien.sql 等初始化）");
                return;
            }
            ensureUserIdColumn();
            ensureBaseRangeLongText();
            jdbcTemplate.execute(
                    "ALTER TABLE mm_job_position MODIFY COLUMN space_id VARCHAR(64) NULL DEFAULT NULL");
            jdbcTemplate.execute(
                    """
                            CREATE TABLE IF NOT EXISTS mm_job_position_space (
                              position_id VARCHAR(64) NOT NULL,
                              space_id VARCHAR(64) NOT NULL,
                              PRIMARY KEY (position_id, space_id),
                              KEY idx_jps_space (space_id),
                              CONSTRAINT fk_jps_position FOREIGN KEY (position_id) REFERENCES mm_job_position (position_id) ON DELETE CASCADE,
                              CONSTRAINT fk_jps_space FOREIGN KEY (space_id) REFERENCES mm_space (space_id)
                            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                            """);
            jdbcTemplate.update(
                    """
                            INSERT IGNORE INTO mm_job_position_space (position_id, space_id)
                            SELECT position_id, space_id FROM mm_job_position
                            WHERE space_id IS NOT NULL AND TRIM(space_id) <> ''
                            """);
            log.info("mm_job_position / mm_job_position_space 结构自检完成（关闭补丁：mienmien.business.schema.auto-patch-job-position=false）");
        } catch (Exception e) {
            log.error(
                    "岗位表结构补丁失败：请手工执行 scripts/migrate-mm-job-position-jdbc-persist.sql 后重启；或检查库账号是否有 ALTER/CREATE 权限。",
                    e);
            throw new IllegalStateException(
                    "岗位表结构补丁失败，岗位相关 SQL 将无法执行。请执行 scripts/migrate-mm-job-position-jdbc-persist.sql 或查看上一行 ERROR 日志。原因: "
                            + e.getMessage(),
                    e);
        }
    }

    private boolean tableExists(String tableName) {
        Integer n =
                jdbcTemplate.queryForObject(
                        """
                                SELECT COUNT(1) FROM information_schema.tables
                                WHERE table_schema = DATABASE() AND table_name = ?
                                """,
                        Integer.class,
                        tableName);
        return n != null && n > 0;
    }

    private void ensureUserIdColumn() {
        Integer n =
                jdbcTemplate.queryForObject(
                        """
                                SELECT COUNT(1) FROM information_schema.columns
                                WHERE table_schema = DATABASE() AND table_name = 'mm_job_position' AND column_name = 'user_id'
                                """,
                        Integer.class);
        if (n == null || n == 0) {
            log.warn("检测到 mm_job_position 缺少 user_id 列，正在执行 ALTER TABLE …");
            jdbcTemplate.execute(
                    "ALTER TABLE mm_job_position ADD COLUMN user_id VARCHAR(64) NOT NULL DEFAULT '' AFTER position_id");
        }
    }

    /** 将 base_range 从 VARCHAR/TEXT 升为 LONGTEXT，避免 jdDetail 等长内容入库失败。 */
    private void ensureBaseRangeLongText() {
        String dataType;
        try {
            dataType =
                    jdbcTemplate.queryForObject(
                            """
                                    SELECT LOWER(DATA_TYPE) FROM information_schema.columns
                                    WHERE table_schema = DATABASE() AND table_name = 'mm_job_position' AND column_name = 'base_range'
                                    """,
                            String.class);
        } catch (Exception e) {
            log.debug("无法读取 mm_job_position.base_range 列类型，跳过扩列: {}", e.getMessage());
            return;
        }
        if (dataType == null) {
            return;
        }
        if ("longtext".equals(dataType)) {
            return;
        }
        log.warn("检测到 mm_job_position.base_range 类型为 {}，将升级为 LONGTEXT …", dataType);
        jdbcTemplate.execute("ALTER TABLE mm_job_position MODIFY COLUMN base_range LONGTEXT NOT NULL");
    }
}
