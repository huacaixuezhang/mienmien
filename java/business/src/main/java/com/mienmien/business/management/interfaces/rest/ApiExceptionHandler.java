package com.mienmien.business.management.interfaces.rest;

import com.mienmien.business.management.domain.DomainException;
import com.mienmien.business.management.domain.ResourceNotFoundException;
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
        }
        return Map.of("code", "BUS-4040", "message", msg);
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> handleGeneric(Exception ex) {
        return Map.of("code", "BUS-4001", "message", "请求处理失败: " + ex.getMessage());
    }
}
