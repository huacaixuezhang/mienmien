package com.mienmien.business.management.application.security;

import com.mienmien.business.management.domain.DomainException;

/**
 * 当前 HTTP 请求关联的 B 端登录用户（由会话过滤器写入，请求结束时清理）。
 */
public final class BusinessRequestActor {
    private static final ThreadLocal<String> USER_ID = new ThreadLocal<>();

    private BusinessRequestActor() {
    }

    public static void setUserId(String userId) {
        USER_ID.set(userId);
    }

    public static void clear() {
        USER_ID.remove();
    }

    public static String requireUserId() {
        String u = USER_ID.get();
        if (u == null || u.isBlank()) {
            throw new DomainException("BUS-4010", "未登录或会话已失效");
        }
        return u;
    }
}
