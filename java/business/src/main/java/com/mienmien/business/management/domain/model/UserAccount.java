package com.mienmien.business.management.domain.model;

import com.mienmien.business.management.domain.DomainException;

import java.time.Instant;
import java.util.Objects;

public final class UserAccount {
    private final String userId;
    private final String phone;
    private final String passwordDigest;
    private final Instant createdAt;
    private Instant updatedAt;

    private UserAccount(String userId, String phone, String passwordDigest, Instant createdAt, Instant updatedAt) {
        this.userId = Objects.requireNonNull(userId);
        this.phone = normalizePhone(phone);
        this.passwordDigest = requireNonBlankSecret(passwordDigest);
        this.createdAt = Objects.requireNonNull(createdAt);
        this.updatedAt = Objects.requireNonNull(updatedAt);
    }

    /**
     * 注册：{@code passwordDigest} 为已编码的口令摘要（如 BCrypt），领域层不对其做 trim 以免破坏摘要。
     */
    public static UserAccount createWithPasswordDigest(String userId, String phone, String passwordDigest) {
        Instant now = Instant.now();
        return new UserAccount(userId, phone, passwordDigest, now, now);
    }

    public static UserAccount restore(String userId, String phone, String passwordDigest, Instant createdAt, Instant updatedAt) {
        return new UserAccount(userId, phone, passwordDigest, createdAt, updatedAt);
    }

    private static String normalizePhone(String phone) {
        if (phone == null) {
            throw new DomainException("BUS-4011", "手机号不能为空");
        }
        String normalized = phone.trim();
        if (!normalized.matches("^1\\d{10}$")) {
            throw new DomainException("BUS-4011", "手机号格式不正确");
        }
        return normalized;
    }

    private static String requireNonBlankSecret(String digest) {
        if (digest == null || digest.isBlank()) {
            throw new DomainException("BUS-4011", "密码不能为空");
        }
        return digest;
    }

    public String getUserId() {
        return userId;
    }

    public String getPhone() {
        return phone;
    }

    /**
     * 持久化与登录校验用：库内为 BCrypt 摘要或历史明文（迁移期）。
     */
    public String getPasswordDigest() {
        return passwordDigest;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
