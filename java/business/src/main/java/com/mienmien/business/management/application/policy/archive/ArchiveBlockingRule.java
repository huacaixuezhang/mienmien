package com.mienmien.business.management.application.policy.archive;

public interface ArchiveBlockingRule {
    long countBlocking(String spaceId);

    String description();
}
