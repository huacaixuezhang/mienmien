package com.mienmien.business.management.domain.repository;

import com.mienmien.business.management.domain.model.Space;

import java.util.List;
import java.util.Optional;

public interface SpaceRepository {
    void save(Space space);

    void update(Space space);

    void deleteById(String spaceId);

    Optional<Space> findById(String spaceId);

    List<Space> findAllActiveOrderByCreatedAtDesc();

    List<Space> findAllRecycledOrderByDeletedAtDesc();

    List<Space> findActiveByOwnerUserIdOrderByCreatedAtDesc(String ownerUserId);

    List<Space> findRecycledByOwnerUserIdOrderByDeletedAtDesc(String ownerUserId);
}
