package com.mienmien.business.management.domain.model;

import com.mienmien.business.management.domain.DomainException;

import java.time.Instant;
import java.util.Objects;

public final class StandardAnswerBank {
    private final String answerId;
    private final String spaceId;
    private String intro;
    private String reason;
    private String strengths;
    private String project;
    private String hr;
    private String cardsJson;
    private Instant updatedAt;

    private StandardAnswerBank(
            String answerId,
            String spaceId,
            String intro,
            String reason,
            String strengths,
            String project,
            String hr,
            String cardsJson,
            Instant updatedAt) {
        this.answerId = Objects.requireNonNull(answerId);
        this.spaceId = Objects.requireNonNull(spaceId);
        this.intro = nonNullText(intro);
        this.reason = nonNullText(reason);
        this.strengths = nonNullText(strengths);
        this.project = nonNullText(project);
        this.hr = nonNullText(hr);
        this.cardsJson = nonNullText(cardsJson);
        this.updatedAt = Objects.requireNonNull(updatedAt);
    }

    public static StandardAnswerBank createNew(
            String answerId,
            String spaceId,
            String intro,
            String reason,
            String strengths,
            String project,
            String hr,
            String cardsJson) {
        if (spaceId == null || spaceId.isBlank()) {
            throw new DomainException("BUS-4001", "spaceId 不能为空");
        }
        return new StandardAnswerBank(
                answerId,
                spaceId.trim(),
                intro,
                reason,
                strengths,
                project,
                hr,
                cardsJson,
                Instant.now()
        );
    }

    public static StandardAnswerBank restore(
            String answerId,
            String spaceId,
            String intro,
            String reason,
            String strengths,
            String project,
            String hr,
            String cardsJson,
            Instant updatedAt) {
        return new StandardAnswerBank(answerId, spaceId, intro, reason, strengths, project, hr, cardsJson, updatedAt);
    }

    public void update(
            String intro,
            String reason,
            String strengths,
            String project,
            String hr,
            String cardsJson) {
        this.intro = nonNullText(intro);
        this.reason = nonNullText(reason);
        this.strengths = nonNullText(strengths);
        this.project = nonNullText(project);
        this.hr = nonNullText(hr);
        this.cardsJson = nonNullText(cardsJson);
        this.updatedAt = Instant.now();
    }

    private static String nonNullText(String text) {
        return text == null ? "" : text.trim();
    }

    public String getAnswerId() {
        return answerId;
    }

    public String getSpaceId() {
        return spaceId;
    }

    public String getIntro() {
        return intro;
    }

    public String getReason() {
        return reason;
    }

    public String getStrengths() {
        return strengths;
    }

    public String getProject() {
        return project;
    }

    public String getHr() {
        return hr;
    }

    public String getCardsJson() {
        return cardsJson;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
