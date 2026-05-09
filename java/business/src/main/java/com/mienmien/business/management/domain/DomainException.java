package com.mienmien.business.management.domain;

/**
 * 领域规则违反时抛出，由接口层映射为 HTTP 错误码。
 */
public class DomainException extends RuntimeException {
    private final String code;

    public DomainException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
