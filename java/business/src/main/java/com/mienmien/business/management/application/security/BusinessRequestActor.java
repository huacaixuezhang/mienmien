package com.mienmien.business.management.application.security;

import com.mienmien.business.management.domain.DomainException;

/** 当前 HTTP 请求关联的登录用户（由会话过滤器写入）。 */
public final class BusinessRequestActor {
    private static final ThreadLocal<String> USER_ID = new ThreadLocal<>();

    private BusinessRequestActor() {
    }

    public static void setUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            USER_ID.remove();
        } else {
            USER_ID.set(userId);
        }
    }

    public static void clear() {
        USER_ID.remove();
    }

    public static String requireUserId() {
        String id = USER_ID.get();
        if (id == null || id.isBlank()) {
            throw new DomainException("BUS-4010", "未登录或会话已失效");
        }
        return id;
    }

    public static String currentUserIdOrNull() {
        return USER_ID.get();
    }
}
