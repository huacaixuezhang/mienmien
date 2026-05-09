package com.mienmien.business.management.application.service;

import com.mienmien.business.management.application.dto.SpaceResponse;
import com.mienmien.business.management.application.security.BusinessRequestActor;
import com.mienmien.business.management.domain.DomainException;
import com.mienmien.business.management.domain.ResourceNotFoundException;
import com.mienmien.business.management.domain.model.Space;
import com.mienmien.business.management.domain.repository.SpaceRepository;
import com.mienmien.business.management.domain.support.ShortIdGenerator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class SpaceApplicationService {
    private final SpaceRepository spaceRepository;
    private final ShortIdGenerator idGenerator;

    public SpaceApplicationService(
            SpaceRepository spaceRepository,
            ShortIdGenerator idGenerator) {
        this.spaceRepository = spaceRepository;
        this.idGenerator = idGenerator;
    }

    @Transactional
    public SpaceResponse createSpace(String name) {
        String actor = BusinessRequestActor.requireUserId();
        String nm = name == null || name.isBlank() ? "默认空间" : name;
        Space space = Space.createNew(idGenerator.newId("sp_"), actor, nm);
        spaceRepository.save(space);
        return SpaceResponse.from(space);
    }

    @Transactional
    public SpaceResponse renameSpace(String spaceId, String newName) {
        String actor = BusinessRequestActor.requireUserId();
        Space space = loadOwnedWritable(spaceId, actor);
        space.rename(newName);
        spaceRepository.update(space);
        return SpaceResponse.from(space);
    }

    @Transactional
    public SpaceResponse archiveSpace(String spaceId) {
        String actor = BusinessRequestActor.requireUserId();
        Space space = loadOwnedWritable(spaceId, actor);
        if (space.isRecycled()) {
            return SpaceResponse.from(space);
        }
        space.moveToRecycleBin();
        spaceRepository.update(space);
        return SpaceResponse.from(space);
    }

    @Transactional
    public SpaceResponse deleteSpace(String spaceId) {
        return archiveSpace(spaceId);
    }

    @Transactional
    public SpaceResponse restoreSpace(String spaceId) {
        String actor = BusinessRequestActor.requireUserId();
        Space space = spaceRepository.findById(spaceId)
                .orElseThrow(() -> new ResourceNotFoundException("空间不存在: " + spaceId));
        if (!space.getOwnerUserId().equals(actor)) {
            throw new DomainException("BUS-4033", "无权访问该空间");
        }
        if (!space.isRecycled()) {
            throw new DomainException("BUS-4001", "仅回收站中的空间可还原");
        }
        space.restoreFromRecycleBin();
        spaceRepository.update(space);
        return SpaceResponse.from(space);
    }

    @Transactional(readOnly = true)
    public SpaceResponse getSpace(String spaceId) {
        String actor = BusinessRequestActor.requireUserId();
        Space space = spaceRepository.findById(spaceId)
                .orElseThrow(() -> new ResourceNotFoundException("空间不存在: " + spaceId));
        if (!space.getOwnerUserId().equals(actor)) {
            throw new DomainException("BUS-4033", "无权访问该空间");
        }
        if (space.isArchived()) {
            throw new DomainException("BUS-4001", "已归档空间不可访问");
        }
        return SpaceResponse.from(space);
    }

    @Transactional(readOnly = true)
    public List<SpaceResponse> listSpaces() {
        String actor = BusinessRequestActor.requireUserId();
        return spaceRepository.findActiveByOwnerUserIdOrderByCreatedAtDesc(actor).stream().map(SpaceResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<SpaceResponse> listRecycleBin() {
        String actor = BusinessRequestActor.requireUserId();
        return spaceRepository.findRecycledByOwnerUserIdOrderByDeletedAtDesc(actor).stream().map(SpaceResponse::from).toList();
    }

    private Space loadOwnedWritable(String spaceId, String actorUserId) {
        Space space = spaceRepository.findById(spaceId)
                .orElseThrow(() -> new ResourceNotFoundException("空间不存在: " + spaceId));
        if (!space.getOwnerUserId().equals(actorUserId)) {
            throw new DomainException("BUS-4033", "无权访问该空间");
        }
        if (space.isArchived()) {
            throw new DomainException("BUS-4001", "已归档空间不可访问");
        }
        if (!"ACTIVE".equals(space.getStatus())) {
            throw new DomainException("BUS-4001", "仅可用空间可执行此操作");
        }
        return space;
    }
}
