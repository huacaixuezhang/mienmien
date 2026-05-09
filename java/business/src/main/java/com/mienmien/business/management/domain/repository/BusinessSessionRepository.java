package com.mienmien.business.management.domain.repository;

import java.time.Instant;
import java.util.Optional;

public interface BusinessSessionRepository {
    void save(String sessionToken, String userId, Instant expiresAt);

    Optional<String> findUserIdByValidToken(String sessionToken);

    void deleteByToken(String sessionToken);
}
