package com.mienmien.business.management.infrastructure.persistence;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mienmien.business.management.domain.model.ResumeDocument;
import com.mienmien.business.management.domain.model.ResumeModule;
import com.mienmien.business.management.domain.repository.ResumeDocumentRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Repository
public class JdbcResumeDocumentRepository implements ResumeDocumentRepository {
    private static final Logger log = LoggerFactory.getLogger(JdbcResumeDocumentRepository.class);

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final RowMapper<ResumeDocument> documentMapper;

    public JdbcResumeDocumentRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.documentMapper = (rs, rowNum) -> {
            String json = rs.getString("modules_json");
            List<ResumeModule> modules;
            try {
                modules = objectMapper.readValue(json, new TypeReference<>() {
                });
            } catch (Exception e) {
                modules = List.of(new ResumeModule("m1", "模块1", ""));
            }
            Timestamp ca = rs.getTimestamp("created_at");
            Timestamp ua = rs.getTimestamp("updated_at");
            Instant created = ca != null ? ca.toInstant() : Instant.now();
            Instant updated = ua != null ? ua.toInstant() : Instant.now();
            return ResumeDocument.restore(
                    rs.getString("resume_id"),
                    rs.getString("user_id"),
                    rs.getString("name"),
                    modules,
                    created,
                    updated
            );
        };
    }

    /**
     * 兼容旧库：早期 {@code mm_resume_document} 含非空 {@code space_id}，与当前「主体 + mm_resume_document_space 关联」模型冲突。
     * 启动时幂等：确保关联表存在、将旧列写入关联表、再将 {@code space_id} 改为可空（以便 INSERT 可不写该列）。
     */
    @PostConstruct
    void alignLegacyResumeDocumentSchema() {
        try {
            jdbcTemplate.execute(
                    """
                            CREATE TABLE IF NOT EXISTS mm_resume_document_space (
                              resume_id VARCHAR(64) NOT NULL,
                              space_id VARCHAR(64) NOT NULL,
                              PRIMARY KEY (resume_id, space_id),
                              KEY idx_rds_space (space_id),
                              CONSTRAINT fk_rds_resume FOREIGN KEY (resume_id) REFERENCES mm_resume_document (resume_id) ON DELETE CASCADE,
                              CONSTRAINT fk_rds_space FOREIGN KEY (space_id) REFERENCES mm_space (space_id)
                            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                            """);
        } catch (Exception e) {
            log.debug("mm_resume_document_space 已存在或创建跳过: {}", e.getMessage());
        }
        try {
            jdbcTemplate.update(
                    """
                            INSERT IGNORE INTO mm_resume_document_space (resume_id, space_id)
                            SELECT resume_id, space_id FROM mm_resume_document
                            WHERE space_id IS NOT NULL AND space_id <> ''
                            """);
        } catch (Exception e) {
            log.debug("旧版 space_id 回填关联表跳过（列可能已不存在）: {}", e.getMessage());
        }
        try {
            jdbcTemplate.execute("ALTER TABLE mm_resume_document MODIFY COLUMN space_id VARCHAR(64) NULL");
        } catch (Exception e) {
            log.debug("space_id 列放宽为可空跳过（新库无该列或已可空）: {}", e.getMessage());
        }
    }

    @Override
    public void save(ResumeDocument document) {
        String json;
        try {
            json = objectMapper.writeValueAsString(document.getModulesView());
        } catch (Exception e) {
            throw new IllegalStateException("序列化模块失败", e);
        }
        jdbcTemplate.update(
                """
                        INSERT INTO mm_resume_document (resume_id, user_id, name, modules_json)
                        VALUES (?,?,?,?)
                        ON DUPLICATE KEY UPDATE name=VALUES(name), modules_json=VALUES(modules_json), user_id=VALUES(user_id)
                        """,
                document.getResumeId(),
                document.getUserId(),
                document.getName(),
                json
        );
    }

    @Override
    public Optional<ResumeDocument> findByResumeId(String resumeId) {
        List<ResumeDocument> list = jdbcTemplate.query(
                "SELECT resume_id, user_id, name, modules_json, created_at, updated_at FROM mm_resume_document WHERE resume_id=?",
                documentMapper,
                resumeId
        );
        return list.stream().findFirst();
    }

    @Override
    public List<ResumeDocument> findBySpaceIdOrderByUpdatedAtDesc(String spaceId) {
        return jdbcTemplate.query(
                """
                        SELECT d.resume_id, d.user_id, d.name, d.modules_json, d.created_at, d.updated_at
                        FROM mm_resume_document d
                        INNER JOIN mm_resume_document_space l ON d.resume_id = l.resume_id
                        WHERE l.space_id = ?
                        ORDER BY d.updated_at DESC
                        """,
                documentMapper,
                spaceId
        );
    }

    @Override
    public List<ResumeDocument> findByUserIdOrderByUpdatedAtDesc(String userId) {
        return jdbcTemplate.query(
                "SELECT resume_id, user_id, name, modules_json, created_at, updated_at FROM mm_resume_document WHERE user_id=? ORDER BY updated_at DESC",
                documentMapper,
                userId
        );
    }

    @Override
    public List<String> findSpaceIdsByResumeId(String resumeId) {
        return jdbcTemplate.query(
                "SELECT space_id FROM mm_resume_document_space WHERE resume_id=? ORDER BY space_id",
                (rs, rowNum) -> rs.getString("space_id"),
                resumeId
        );
    }

    @Override
    public void addSpaceLink(String resumeId, String spaceId) {
        jdbcTemplate.update(
                "INSERT IGNORE INTO mm_resume_document_space (resume_id, space_id) VALUES (?,?)",
                resumeId,
                spaceId
        );
    }

    @Override
    public void deleteSpaceLink(String resumeId, String spaceId) {
        jdbcTemplate.update("DELETE FROM mm_resume_document_space WHERE resume_id=? AND space_id=?", resumeId, spaceId);
    }

    @Override
    public int countSpaceLinks(String resumeId) {
        Integer n = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM mm_resume_document_space WHERE resume_id=?",
                Integer.class,
                resumeId
        );
        return n != null ? n : 0;
    }

    @Override
    public void deleteByResumeId(String resumeId) {
        jdbcTemplate.update("DELETE FROM mm_resume_document_space WHERE resume_id=?", resumeId);
        jdbcTemplate.update("DELETE FROM mm_resume_document WHERE resume_id=?", resumeId);
    }
}
