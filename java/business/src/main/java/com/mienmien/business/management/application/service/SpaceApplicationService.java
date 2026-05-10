package com.mienmien.business.management.application.service;

import com.mienmien.business.management.application.dto.SpaceResponse;
import com.mienmien.business.management.application.security.BusinessRequestActor;
import com.mienmien.business.management.application.support.ApplicationSpaceGuard;
import com.mienmien.business.management.domain.DomainException;
import com.mienmien.business.management.domain.ResourceNotFoundException;
import com.mienmien.business.management.domain.model.Space;
import com.mienmien.business.management.domain.repository.SpaceRepository;
import com.mienmien.business.management.domain.support.ShortIdGenerator;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SpaceApplicationService {
    private final SpaceRepository spaceRepository;
    private final ShortIdGenerator shortIdGenerator;

    public SpaceApplicationService(SpaceRepository spaceRepository, ShortIdGenerator shortIdGenerator) {
        this.spaceRepository = spaceRepository;
        this.shortIdGenerator = shortIdGenerator;
    }

    public SpaceResponse createSpace(String name) {
        String ownerUserId = BusinessRequestActor.requireUserId();
        String spaceId = shortIdGenerator.newId("s");
        Space space = Space.createNew(spaceId, ownerUserId, name);
        spaceRepository.save(space);
        return toResponse(space);
    }

    public SpaceResponse getSpace(String spaceId) {
        Space space = ApplicationSpaceGuard.requireOwnedActiveSpace(spaceId, spaceRepository);
        return toResponse(space);
    }

    public List<SpaceResponse> listSpaces() {
        String ownerUserId = BusinessRequestActor.requireUserId();
        return spaceRepository.findActiveByOwnerUserIdOrderByCreatedAtDesc(ownerUserId).stream()
                .map(SpaceApplicationService::toResponse)
                .toList();
    }

    public List<SpaceResponse> listRecycleBin() {
        String ownerUserId = BusinessRequestActor.requireUserId();
        return spaceRepository.findRecycledByOwnerUserIdOrderByDeletedAtDesc(ownerUserId).stream()
                .map(SpaceApplicationService::toResponse)
                .toList();
    }

    public SpaceResponse renameSpace(String spaceId, String newName) {
        ApplicationSpaceGuard.requireOwnedActiveSpace(spaceId, spaceRepository);
        Space space = spaceRepository.findById(spaceId).orElseThrow(() -> new ResourceNotFoundException("空间不存在"));
        space.rename(newName);
        spaceRepository.update(space);
        return toResponse(space);
    }

    /** 移入回收站 */
    public SpaceResponse archiveSpace(String spaceId) {
        String actor = BusinessRequestActor.requireUserId();
        Space space = spaceRepository.findById(spaceId).orElseThrow(() -> new ResourceNotFoundException("空间不存在"));
        if (!space.getOwnerUserId().equals(actor)) {
            throw new DomainException("BUS-4033", "无权访问该空间");
        }
        if (space.isArchived()) {
            throw new DomainException("BUS-4001", "已归档空间不可删除");
        }
        space.moveToRecycleBin();
        spaceRepository.update(space);
        return toResponse(space);
    }

    public SpaceResponse restoreSpace(String spaceId) {
        String actor = BusinessRequestActor.requireUserId();
        Space space = spaceRepository.findById(spaceId).orElseThrow(() -> new ResourceNotFoundException("空间不存在"));
        if (!space.getOwnerUserId().equals(actor)) {
            throw new DomainException("BUS-4033", "无权访问该空间");
        }
        space.restoreFromRecycleBin();
        spaceRepository.update(space);
        return toResponse(space);
    }

    public SpaceResponse deleteSpace(String spaceId) {
        String actor = BusinessRequestActor.requireUserId();
        Space space = spaceRepository.findById(spaceId).orElseThrow(() -> new ResourceNotFoundException("空间不存在"));
        if (!space.getOwnerUserId().equals(actor)) {
            throw new DomainException("BUS-4033", "无权访问该空间");
        }
        if (!space.isRecycled()) {
            throw new DomainException("BUS-4001", "仅能永久删除回收站内的空间");
        }
        SpaceResponse snapshot = toResponse(space);
        spaceRepository.deleteById(spaceId);
        return snapshot;
    }

    private static SpaceResponse toResponse(Space s) {
        return new SpaceResponse(
                s.getSpaceId(),
                s.getOwnerUserId(),
                s.getName(),
                s.getStatus(),
                s.getCreatedAt(),
                s.getUpdatedAt(),
                s.getDeletedAt()
        );
    }
}
