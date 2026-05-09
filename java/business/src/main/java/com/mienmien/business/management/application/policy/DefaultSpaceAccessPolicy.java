package com.mienmien.business.management.application.policy;

import com.mienmien.business.management.domain.DomainException;
import com.mienmien.business.management.domain.ResourceNotFoundException;
import com.mienmien.business.management.domain.model.Space;
import com.mienmien.business.management.domain.repository.SpaceRepository;
import org.springframework.stereotype.Component;

@Component
public class DefaultSpaceAccessPolicy implements SpaceAccessPolicy {
    private final SpaceRepository spaceRepository;

    public DefaultSpaceAccessPolicy(SpaceRepository spaceRepository) {
        this.spaceRepository = spaceRepository;
    }

    @Override
    public Space requireReadableSpaceForActor(String spaceId, String actorUserId) {
        Space space = spaceRepository.findById(spaceId)
                .orElseThrow(() -> new ResourceNotFoundException("空间不存在: " + spaceId));
        if (!space.getOwnerUserId().equals(actorUserId)) {
            throw new DomainException("BUS-4033", "无权访问该空间");
        }
        if (space.isArchived()) {
            throw new DomainException("BUS-4001", "已归档空间不可访问");
        }
        return space;
    }

    @Override
    public Space requireWritableSpaceForActor(String spaceId, String actorUserId) {
        Space space = requireReadableSpaceForActor(spaceId, actorUserId);
        if (!"ACTIVE".equals(space.getStatus())) {
            throw new DomainException("BUS-4001", "仅可用空间可执行此操作");
        }
        return space;
    }
}
