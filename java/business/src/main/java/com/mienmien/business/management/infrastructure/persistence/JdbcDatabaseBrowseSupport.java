package com.mienmien.business.management.infrastructure.persistence;

import com.mienmien.business.management.domain.DomainException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 只读浏览当前库中的业务表（表名白名单 + 必须在 information_schema 中存在）。
 */
@Component
public class JdbcDatabaseBrowseSupport {
    private final JdbcTemplate jdbcTemplate;

    public JdbcDatabaseBrowseSupport(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<String> listTablesInCurrentSchema() {
        return jdbcTemplate.query(
                """
                        SELECT TABLE_NAME FROM information_schema.TABLES
                        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_TYPE = 'BASE TABLE'
                        ORDER BY TABLE_NAME
                        """,
                (rs, rowNum) -> rs.getString(1));
    }

    /** 一次查询数据行，并返回列名（有行时取自首行；无行时取自 information_schema）。 */
    public TablePage queryTablePage(String tableName, int offset, int limit) {
        requireKnownTable(tableName);
        int lim = Math.min(500, Math.max(1, limit));
        int off = Math.max(0, offset);
        List<Map<String, Object>> rows =
                jdbcTemplate.query(
                        "SELECT * FROM `" + tableName + "` LIMIT ? OFFSET ?",
                        (rs, rowNum) -> mapRow(rs),
                        lim,
                        off);
        List<String> columns;
        if (rows.isEmpty()) {
            columns = listColumnNames(tableName);
        } else {
            columns = new ArrayList<>(rows.get(0).keySet());
        }
        long total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM `" + tableName + "`", Long.class);
        return new TablePage(columns, rows, total, off, lim);
    }

    private List<String> listColumnNames(String tableName) {
        return jdbcTemplate.query(
                """
                        SELECT COLUMN_NAME FROM information_schema.COLUMNS
                        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?
                        ORDER BY ORDINAL_POSITION
                        """,
                (rs, rowNum) -> rs.getString(1),
                tableName);
    }

    private void requireKnownTable(String tableName) {
        if (tableName == null || !tableName.matches("[a-zA-Z0-9_]+")) {
            throw new DomainException("BUS-4001", "非法表名");
        }
        Integer n =
                jdbcTemplate.queryForObject(
                        """
                                SELECT COUNT(*) FROM information_schema.TABLES
                                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND TABLE_TYPE = 'BASE TABLE'
                                """,
                        Integer.class,
                        tableName);
        if (n == null || n == 0) {
            throw new DomainException("BUS-4041", "表不存在: " + tableName);
        }
    }

    private static Map<String, Object> mapRow(ResultSet rs) throws java.sql.SQLException {
        ResultSetMetaData md = rs.getMetaData();
        int n = md.getColumnCount();
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 1; i <= n; i++) {
            String label = md.getColumnLabel(i);
            map.put(label != null && !label.isBlank() ? label : md.getColumnName(i), rs.getObject(i));
        }
        return map;
    }

    public record TablePage(
            List<String> columnNames, List<Map<String, Object>> rows, long rowCount, int offset, int limit) {
    }
}
