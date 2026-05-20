package com.mienmien.business.management.domain.repository;

import com.mienmien.business.management.domain.model.UserInterviewerStyle;

import java.util.List;
import java.util.Optional;

public interface UserInterviewerStyleRepository {

    void insert(UserInterviewerStyle style);

    void update(UserInterviewerStyle style);

    void deleteByIdAndOwnerUserId(String styleId, String ownerUserId);

    Optional<UserInterviewerStyle> findByIdAndOwnerUserId(String styleId, String ownerUserId);

    List<UserInterviewerStyle> findByOwnerUserIdOrderByUpdatedAtDesc(String ownerUserId);
}
