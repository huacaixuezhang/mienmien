package com.mienmien.business.management.infrastructure.persistence;

import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 将 {@code mm_interview_record.summary} 从 {@code TEXT}（64KB 上限）扩为 {@code MEDIUMTEXT}，避免多轮面试 +
 * 语音逐题 JSON 写入时截断。
 */
@Component
@Order(3)
public class MmInterviewRecordSummaryColumnBootstrap implements ApplicationRunner {

    private static final Logger log =
            LoggerFactory.getLogger(MmInterviewRecordSummaryColumnBootstrap.class);

    private final JdbcTemplate jdbcTemplate;
    private final boolean enabled;

    public MmInterviewRecordSummaryColumnBootstrap(
            JdbcTemplate jdbcTemplate,
            @Value("${mienmien.business.schema.auto-patch-interview-record-summary-column:true}")
                    boolean enabled) {
        this.jdbcTemplate = jdbcTemplate;
        this.enabled = enabled;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) {
            log.debug("已禁用 mienmien.business.schema.auto-patch-interview-record-summary-column，跳过 summary 列扩容");
            return;
        }
        try {
            if (!tableExists("mm_interview_record")) {
                log.warn("库中不存在表 mm_interview_record，跳过 summary 列类型自检");
                return;
            }
            String dataType = querySummaryDataType();
            if (dataType == null) {
                log.warn("未找到 mm_interview_record.summary 列，跳过扩容");
                return;
            }
            if ("mediumtext".equalsIgnoreCase(dataType)) {
                log.debug("mm_interview_record.summary 已为 MEDIUMTEXT，跳过");
                return;
            }
            if (!"text".equalsIgnoreCase(dataType)) {
                log.warn(
                        "mm_interview_record.summary 当前类型为 {}，非 TEXT；为安全起见不自动 ALTER，请人工评估",
                        dataType);
                return;
            }
            jdbcTemplate.execute(
                    "ALTER TABLE mm_interview_record MODIFY COLUMN summary MEDIUMTEXT NOT NULL");
            log.info("已将 mm_interview_record.summary 从 TEXT 调整为 MEDIUMTEXT");
        } catch (Exception e) {
            log.error("调整 mm_interview_record.summary 列类型失败", e);
            throw new IllegalStateException("summary 列扩容失败: " + e.getMessage(), e);
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

    private String querySummaryDataType() {
        List<Map<String, Object>> rows =
                jdbcTemplate.queryForList(
                        """
                                SELECT DATA_TYPE FROM information_schema.COLUMNS
                                WHERE TABLE_SCHEMA = DATABASE()
                                  AND TABLE_NAME = 'mm_interview_record'
                                  AND COLUMN_NAME = 'summary'
                                LIMIT 1
                                """);
        if (rows.isEmpty()) {
            return null;
        }
        Object v = rows.get(0).get("DATA_TYPE");
        return v == null ? null : String.valueOf(v);
    }
}
