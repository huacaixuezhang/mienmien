package com.mienmien.business.management.application.service;

import com.mienmien.business.management.application.dto.JobPositionResponse;
import com.mienmien.business.management.application.security.BusinessRequestActor;
import com.mienmien.business.management.application.support.ApplicationSpaceGuard;
import com.mienmien.business.management.domain.DomainException;
import com.mienmien.business.management.domain.ResourceNotFoundException;
import com.mienmien.business.management.domain.model.JobPosition;
import com.mienmien.business.management.domain.repository.SpaceRepository;
import com.mienmien.business.management.domain.support.ShortIdGenerator;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 进程内存储；岗位主体与空间多对多关联。
 */
@Service
public class JobPositionApplicationService {
    private final SpaceRepository spaceRepository;
    private final ShortIdGenerator shortIdGenerator;
    private final ConcurrentHashMap<String, JobPosition> positionsById = new ConcurrentHashMap<>();
    /** positionId -> 关联的 spaceId 集合 */
    private final ConcurrentHashMap<String, Set<String>> positionSpaces = new ConcurrentHashMap<>();

    public JobPositionApplicationService(SpaceRepository spaceRepository, ShortIdGenerator shortIdGenerator) {
        this.spaceRepository = spaceRepository;
        this.shortIdGenerator = shortIdGenerator;
    }

    public JobPositionResponse create(String spaceId, String title, String company, String location, String baseRange) {
        if (spaceId != null && !spaceId.isBlank()) {
            ApplicationSpaceGuard.requireOwnedActiveSpace(spaceId, spaceRepository);
        }
        String userId = BusinessRequestActor.requireUserId();
        String positionId = shortIdGenerator.newId("p");
        JobPosition jp = JobPosition.createNew(positionId, userId, title, company, location, baseRange);
        positionsById.put(positionId, jp);
        if (spaceId != null && !spaceId.isBlank()) {
            addSpaceLink(positionId, spaceId);
        }
        return toResponse(jp, spaceIdsOf(positionId));
    }

    public List<JobPositionResponse> listBySpace(String spaceId) {
        ApplicationSpaceGuard.requireOwnedActiveSpace(spaceId, spaceRepository);
        return positionsById.values().stream()
                .filter(j -> spaceIdsOf(j.getPositionId()).contains(spaceId))
                .sorted(Comparator.comparing(JobPosition::getUpdatedAt).reversed())
                .map(j -> toResponse(j, spaceIdsOf(j.getPositionId())))
                .toList();
    }

    public List<JobPositionResponse> listMine() {
        String userId = BusinessRequestActor.requireUserId();
        return positionsById.values().stream()
                .filter(j -> userId.equals(j.getUserId()))
                .sorted(Comparator.comparing(JobPosition::getUpdatedAt).reversed())
                .map(j -> toResponse(j, spaceIdsOf(j.getPositionId())))
                .toList();
    }

    public JobPositionResponse getForOwner(String positionId) {
        JobPosition jp = positionsById.get(positionId);
        if (jp == null) {
            throw new ResourceNotFoundException("岗位不存在");
        }
        assertActorOwns(jp);
        return toResponse(jp, spaceIdsOf(positionId));
    }

    public JobPositionResponse update(String positionId, String title, String company, String location, String baseRange) {
        JobPosition jp = positionsById.get(positionId);
        if (jp == null) {
            throw new ResourceNotFoundException("岗位不存在");
        }
        assertActorOwns(jp);
        jp.updateProfile(title, company, location, baseRange);
        return toResponse(jp, spaceIdsOf(positionId));
    }

    public JobPositionResponse close(String positionId) {
        JobPosition jp = positionsById.get(positionId);
        if (jp == null) {
            throw new ResourceNotFoundException("岗位不存在");
        }
        assertActorOwns(jp);
        jp.markClosed();
        return toResponse(jp, spaceIdsOf(positionId));
    }

    /** 删除岗位主体及全部空间关联 */
    public void deleteEntire(String positionId) {
        JobPosition jp = positionsById.get(positionId);
        if (jp == null) {
            throw new ResourceNotFoundException("岗位不存在");
        }
        assertActorOwns(jp);
        positionSpaces.remove(positionId);
        positionsById.remove(positionId);
    }

    public JobPositionResponse linkToSpace(String spaceId, String positionId) {
        ApplicationSpaceGuard.requireOwnedActiveSpace(spaceId, spaceRepository);
        JobPosition jp = positionsById.get(positionId);
        if (jp == null) {
            throw new ResourceNotFoundException("岗位不存在");
        }
        assertActorOwns(jp);
        addSpaceLink(positionId, spaceId);
        return toResponse(jp, spaceIdsOf(positionId));
    }

    /** 从指定空间解除关联（不删除岗位主体） */
    public JobPositionResponse unlinkFromSpace(String spaceId, String positionId) {
        ApplicationSpaceGuard.requireOwnedActiveSpace(spaceId, spaceRepository);
        JobPosition jp = positionsById.get(positionId);
        if (jp == null) {
            throw new ResourceNotFoundException("岗位不存在");
        }
        assertActorOwns(jp);
        Set<String> s = positionSpaces.get(positionId);
        if (s != null) {
            s.remove(spaceId);
            if (s.isEmpty()) {
                positionSpaces.remove(positionId);
            }
        }
        return toResponse(jp, spaceIdsOf(positionId));
    }

    private void assertActorOwns(JobPosition jp) {
        String actor = BusinessRequestActor.requireUserId();
        if (!jp.getUserId().equals(actor)) {
            throw new DomainException("BUS-4033", "无权操作该岗位");
        }
    }

    private void addSpaceLink(String positionId, String spaceId) {
        positionSpaces.computeIfAbsent(positionId, k -> ConcurrentHashMap.newKeySet()).add(spaceId);
    }

    private List<String> spaceIdsOf(String positionId) {
        Set<String> s = positionSpaces.get(positionId);
        if (s == null || s.isEmpty()) {
            return List.of();
        }
        return s.stream().sorted().toList();
    }

    private static JobPositionResponse toResponse(JobPosition j, List<String> spaceIds) {
        String first = spaceIds != null && !spaceIds.isEmpty() ? spaceIds.get(0) : "";
        return new JobPositionResponse(
                j.getPositionId(),
                first,
                spaceIds == null ? List.of() : List.copyOf(spaceIds),
                j.getTitle(),
                j.getCompany(),
                j.getLocation(),
                j.getBaseRange(),
                j.getStatus(),
                j.getCreatedAt(),
                j.getUpdatedAt()
        );
    }
}
