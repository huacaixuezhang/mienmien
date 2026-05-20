package com.mienmien.business.management.domain.repository;

import com.mienmien.business.management.domain.model.UserInterviewerRole;
import java.util.List;
import java.util.Optional;

public interface UserInterviewerRoleRepository {

    void insert(UserInterviewerRole role);

    void update(UserInterviewerRole role);

    void deleteByIdAndOwnerUserId(String roleId, String ownerUserId);

    Optional<UserInterviewerRole> findByIdAndOwnerUserId(String roleId, String ownerUserId);

    List<UserInterviewerRole> findByOwnerUserIdOrderByUpdatedAtDesc(String ownerUserId);

    /** 同一用户下角色代号唯一（不区分大小写）。 */
    Optional<UserInterviewerRole> findByOwnerUserIdAndRoleCodeIgnoreCase(String ownerUserId, String roleCode);
}
