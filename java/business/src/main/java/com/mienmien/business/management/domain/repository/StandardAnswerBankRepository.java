package com.mienmien.business.management.domain.repository;

import com.mienmien.business.management.domain.model.StandardAnswerBank;

import java.util.Optional;

public interface StandardAnswerBankRepository {
    void save(StandardAnswerBank bank);

    void update(StandardAnswerBank bank);

    Optional<StandardAnswerBank> findBySpaceId(String spaceId);

    long countBySpaceId(String spaceId);
}
