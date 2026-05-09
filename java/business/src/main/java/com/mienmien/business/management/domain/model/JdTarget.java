package com.mienmien.business.management.domain.model;

import com.mienmien.business.management.domain.DomainException;

import java.util.Objects;

public final class JdTarget {
    private final String jdId;
    private final String spaceId;
    private final String sourceType;
    private final String rawText;
    private final String focusPoints;

    private JdTarget(String jdId, String spaceId, String sourceType, String rawText, String focusPoints) {
        this.jdId = Objects.requireNonNull(jdId);
        this.spaceId = Objects.requireNonNull(spaceId);
        this.sourceType = Objects.requireNonNull(sourceType);
        this.rawText = Objects.requireNonNull(rawText);
        this.focusPoints = Objects.requireNonNull(focusPoints);
    }

    public static JdTarget createManual(String jdId, String spaceId, String rawText, String focusPoints) {
        if (spaceId == null || spaceId.isBlank()) {
            throw new DomainException("BUS-4001", "spaceId 不能为空");
        }
        return new JdTarget(jdId, spaceId, "manual",
                rawText == null ? "" : rawText,
                focusPoints == null ? "" : focusPoints);
    }

    /** 持久化回读 */
    public static JdTarget restore(String jdId, String spaceId, String sourceType, String rawText, String focusPoints) {
        return new JdTarget(jdId, spaceId, sourceType, rawText, focusPoints);
    }

    public String getJdId() {
        return jdId;
    }

    public String getSpaceId() {
        return spaceId;
    }

    public String getSourceType() {
        return sourceType;
    }

    public String getRawText() {
        return rawText;
    }

    public String getFocusPoints() {
        return focusPoints;
    }
}
