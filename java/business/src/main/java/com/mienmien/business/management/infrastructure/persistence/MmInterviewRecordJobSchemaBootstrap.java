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
 * 与 {@code scripts/migrate-mm-interview-record-job.sql} 等价：确保存在 {@code mm_interview_record_job}，
 * 避免未跑迁移时列表/写入面试触发「表不存在」。
 */
@Component
@Order(2)
public class MmInterviewRecordJobSchemaBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(MmInterviewRecordJobSchemaBootstrap.class);

    private final JdbcTemplate jdbcTemplate;
    private final boolean enabled;

    public MmInterviewRecordJobSchemaBootstrap(
            JdbcTemplate jdbcTemplate,
            @Value("${mienmien.business.schema.auto-patch-interview-record-job:true}") boolean enabled) {
        this.jdbcTemplate = jdbcTemplate;
        this.enabled = enabled;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) {
            log.debug("已禁用 mienmien.business.schema.auto-patch-interview-record-job，跳过面试-岗位绑定表自检");
            return;
        }
        try {
            if (!tableExists("mm_interview_record")) {
                log.warn("库中不存在表 mm_interview_record，跳过 mm_interview_record_job 创建");
                return;
            }
            if (!tableExists("mm_job_position")) {
                log.warn("库中不存在表 mm_job_position，跳过 mm_interview_record_job 创建（外键依赖）");
                return;
            }
            jdbcTemplate.execute(
                    """
                            CREATE TABLE IF NOT EXISTS mm_interview_record_job (
                              record_id VARCHAR(64) NOT NULL PRIMARY KEY,
                              position_id VARCHAR(64) NOT NULL,
                              CONSTRAINT fk_irj_record FOREIGN KEY (record_id) REFERENCES mm_interview_record (record_id) ON DELETE CASCADE,
                              CONSTRAINT fk_irj_position FOREIGN KEY (position_id) REFERENCES mm_job_position (position_id) ON DELETE CASCADE
                            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                            """);
            log.debug("已确保 mm_interview_record_job 存在（关闭补丁：mienmien.business.schema.auto-patch-interview-record-job=false）");
        } catch (Exception e) {
            log.error(
                    "创建/校验 mm_interview_record_job 失败：请手工执行 scripts/migrate-mm-interview-record-job.sql 后重启。",
                    e);
            throw new IllegalStateException(
                    "面试-岗位绑定表自检失败。请执行 scripts/migrate-mm-interview-record-job.sql 或查看上一行 ERROR 日志。原因: "
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
}
