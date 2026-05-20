package com.mienmien.business.management.interfaces.rest;

import com.mienmien.business.management.domain.DomainException;
import com.mienmien.business.management.domain.ResourceNotFoundException;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(DomainException.class)
    public ResponseEntity<Map<String, String>> handleDomain(DomainException ex) {
        HttpStatus st = switch (ex.getCode()) {
            case "BUS-4091", "BUS-4092", "BUS-4093" -> HttpStatus.CONFLICT;
            case "BUS-4010", "BUS-4012" -> HttpStatus.UNAUTHORIZED;
            case "BUS-4033" -> HttpStatus.FORBIDDEN;
            case "BUS-5020" -> HttpStatus.BAD_GATEWAY;
            default -> HttpStatus.BAD_REQUEST;
        };
        return ResponseEntity.status(st).body(Map.of("code", ex.getCode(), "message", ex.getMessage()));
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String, String> handleResourceNotFound(ResourceNotFoundException ex) {
        return Map.of("code", "BUS-4041", "message", ex.getMessage());
    }

    @ExceptionHandler(EmptyResultDataAccessException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String, String> handleJdbcNotFound() {
        return Map.of("code", "BUS-4041", "message", "资源不存在");
    }

    @ExceptionHandler(DataAccessException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Map<String, String> handleDataAccess(DataAccessException ex) {
        String root =
                ex.getRootCause() != null ? ex.getRootCause().getMessage() : ex.getMessage();
        String hint = "";
        if (root != null
                && (root.contains("user_id")
                        || root.contains("mm_job_position_space")
                        || root.contains("Unknown column"))) {
            hint =
                    " 若刚升级后端，请在目标库执行 scripts/migrate-mm-job-position-jdbc-persist.sql，或保持启动补丁开启（mienmien.business.schema.auto-patch-job-position=true / 环境变量 AUTO_PATCH_JOB_SCHEMA=true）后重启。";
        } else if (root != null && root.contains("mm_interview_record_job")) {
            hint =
                    " 缺少面试-岗位绑定表：请执行 scripts/migrate-mm-interview-record-job.sql，或保持 mienmien.business.schema.auto-patch-interview-record-job=true（环境变量 AUTO_PATCH_INTERVIEW_JOB_SCHEMA）后重启。";
        } else if (root != null && root.contains("base_range")) {
            hint =
                    " 岗位扩展字段过长：请执行 scripts/migrate-mm-job-position-base-range-expand.sql（将 base_range 升为 LONGTEXT）或重启 business 以触发启动补丁中的 base_range 扩列。";
        }
        return Map.of("code", "BUS-5030", "message", "数据访问失败: " + root + hint);
    }

    /**
     * 无 Controller 映射时 Spring 会尝试静态资源并抛出该异常；此前落入 {@link #handleGeneric} 会变成 BUS-4001 误导排查。
     */
    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String, String> handleNoResourceFound(NoResourceFoundException ex) {
        String path = ex.getResourcePath();
        String msg = "HTTP 路径未匹配到接口: " + path;
        if (path != null && path.contains("resume-documents")) {
            msg += "。请确认请求的是 business 服务（默认 8080）、路径含 /api/v1/business，并已重新编译部署包含聚合简历接口的版本后重启进程。";
        } else if (path != null && (path.contains("parse-jd") || path.contains("parse-job-position"))) {
            msg += "。请确认 business 已重新编译并重启；岗位 JD 解析亦可使用 POST /api/v1/business/jd-targets/parse-job-position（与 /job-positions/parse-jd 等价）。";
        }
        return Map.of("code", "BUS-4040", "message", msg);
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> handleGeneric(Exception ex) {
        return Map.of("code", "BUS-4001", "message", "请求处理失败: " + ex.getMessage());
    }
}
