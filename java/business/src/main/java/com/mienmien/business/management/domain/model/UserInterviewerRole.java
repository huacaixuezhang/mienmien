package com.mienmien.business.management.domain.model;

import com.mienmien.business.management.domain.DomainException;
import java.time.Instant;
import java.util.Objects;

/**
 * 用户自定义的「面试官角色」定义：角色代号、面试内容范围、侧重点等，供面试记录中「面试官信息」与团队对齐使用。
 */
public final class UserInterviewerRole {
    private final String roleId;
    private final String userId;
    private String roleCode;
    private String roleName;
    private String interviewContent;
    private String focusPoints;
    private String evaluationHint;
    private final Instant createdAt;
    private Instant updatedAt;

    private UserInterviewerRole(
            String roleId,
            String userId,
            String roleCode,
            String roleName,
            String interviewContent,
            String focusPoints,
            String evaluationHint,
            Instant createdAt,
            Instant updatedAt) {
        this.roleId = Objects.requireNonNull(roleId);
        this.userId = Objects.requireNonNull(userId);
        this.roleCode = Objects.requireNonNull(roleCode);
        this.roleName = Objects.requireNonNull(roleName);
        this.interviewContent = Objects.requireNonNull(interviewContent);
        this.focusPoints = Objects.requireNonNull(focusPoints);
        this.evaluationHint = evaluationHint == null ? "" : evaluationHint;
        this.createdAt = Objects.requireNonNull(createdAt);
        this.updatedAt = Objects.requireNonNull(updatedAt);
    }

    public static UserInterviewerRole createNew(
            String roleId,
            String userId,
            String roleCode,
            String roleName,
            String interviewContent,
            String focusPoints,
            String evaluationHint) {
        validateUser(userId);
        String code = normalizeRoleCode(roleCode);
        validateRoleCode(code);
        validateRoleName(roleName);
        validateLongText("面试内容", interviewContent, true);
        validateLongText("面试侧重点", focusPoints, true);
        String hint = evaluationHint == null ? "" : evaluationHint.trim();
        Instant now = Instant.now();
        return new UserInterviewerRole(
                roleId,
                userId,
                code,
                roleName.trim(),
                interviewContent.trim(),
                focusPoints.trim(),
                hint,
                now,
                now);
    }

    public static UserInterviewerRole restore(
            String roleId,
            String userId,
            String roleCode,
            String roleName,
            String interviewContent,
            String focusPoints,
            String evaluationHint,
            Instant createdAt,
            Instant updatedAt) {
        return new UserInterviewerRole(
                roleId,
                userId,
                roleCode,
                roleName,
                interviewContent,
                focusPoints,
                evaluationHint == null ? "" : evaluationHint,
                createdAt,
                updatedAt);
    }

    public void updateProfile(
            String roleCode, String roleName, String interviewContent, String focusPoints, String evaluationHint) {
        String code = normalizeRoleCode(roleCode);
        validateRoleCode(code);
        validateRoleName(roleName);
        validateLongText("面试内容", interviewContent, true);
        validateLongText("面试侧重点", focusPoints, true);
        String hint = evaluationHint == null ? "" : evaluationHint.trim();
        this.roleCode = code;
        this.roleName = roleName.trim();
        this.interviewContent = interviewContent.trim();
        this.focusPoints = focusPoints.trim();
        this.evaluationHint = hint;
        this.updatedAt = Instant.now();
    }

    private static void validateUser(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new DomainException("BUS-4001", "userId 不能为空");
        }
    }

    private static void validateRoleName(String roleName) {
        if (roleName == null || roleName.isBlank()) {
            throw new DomainException("BUS-4001", "角色名称不能为空");
        }
        if (roleName.length() > 256) {
            throw new DomainException("BUS-4001", "角色名称过长");
        }
    }

    private static void validateLongText(String label, String text, boolean required) {
        if (text == null || text.isBlank()) {
            if (required) {
                throw new DomainException("BUS-4001", label + "不能为空");
            }
        }
    }

    /** 与前端下拉、面试记录里 interviewer.role 对齐的短代号，如 HR、peer、ld、+1。 */
    public static String normalizeRoleCode(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.trim();
    }

    private static void validateRoleCode(String code) {
        if (code.isEmpty()) {
            throw new DomainException("BUS-4001", "角色代号不能为空");
        }
        if (code.length() > 64) {
            throw new DomainException("BUS-4001", "角色代号过长（最多 64 字符）");
        }
        for (int i = 0; i < code.length(); i++) {
            char c = code.charAt(i);
            if (Character.isLetterOrDigit(c)
                    || c == '_'
                    || c == '+'
                    || c == '-'
                    || c == '.') {
                continue;
            }
            throw new DomainException(
                    "BUS-4001", "角色代号仅允许字母、数字及 _ + - .（便于与面试记录中的标签一致）");
        }
    }

    public String getRoleId() {
        return roleId;
    }

    public String getUserId() {
        return userId;
    }

    public String getRoleCode() {
        return roleCode;
    }

    public String getRoleName() {
        return roleName;
    }

    public String getInterviewContent() {
        return interviewContent;
    }

    public String getFocusPoints() {
        return focusPoints;
    }

    public String getEvaluationHint() {
        return evaluationHint;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
