package com.mienmien.business.management.application.support;

import com.mienmien.business.management.application.security.BusinessRequestActor;
import com.mienmien.business.management.domain.DomainException;
import com.mienmien.business.management.domain.ResourceNotFoundException;
import com.mienmien.business.management.domain.model.Space;
import com.mienmien.business.management.domain.repository.SpaceRepository;

/** 校验当前登录用户是否拥有指定空间（ACTIVE 且未删除）。 */
public final class ApplicationSpaceGuard {
    private ApplicationSpaceGuard() {
    }

    public static Space requireOwnedActiveSpace(String spaceId, SpaceRepository spaceRepository) {
        String actorUserId = BusinessRequestActor.requireUserId();
        Space space = spaceRepository.findById(spaceId).orElseThrow(() -> new ResourceNotFoundException("空间不存在"));
        if (!space.getOwnerUserId().equals(actorUserId)) {
            throw new DomainException("BUS-4033", "无权访问该空间");
        }
        if (!"ACTIVE".equals(space.getStatus()) || space.getDeletedAt() != null) {
            throw new DomainException("BUS-4001", "空间不可用");
        }
        return space;
    }
}
