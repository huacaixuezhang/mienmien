package com.mienmien.business.management.application.service;

import com.mienmien.business.management.application.dto.UserInterviewerRoleResponse;
import com.mienmien.business.management.application.security.BusinessRequestActor;
import com.mienmien.business.management.domain.DomainException;
import com.mienmien.business.management.domain.ResourceNotFoundException;
import com.mienmien.business.management.domain.model.UserInterviewerRole;
import com.mienmien.business.management.domain.repository.UserInterviewerRoleRepository;
import com.mienmien.business.management.domain.support.ShortIdGenerator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserInterviewerRoleApplicationService {

    private final UserInterviewerRoleRepository repository;
    private final ShortIdGenerator shortIdGenerator;

    public UserInterviewerRoleApplicationService(
            UserInterviewerRoleRepository repository, ShortIdGenerator shortIdGenerator) {
        this.repository = repository;
        this.shortIdGenerator = shortIdGenerator;
    }

    public List<UserInterviewerRoleResponse> listMine() {
        String userId = BusinessRequestActor.requireUserId();
        return repository.findByOwnerUserIdOrderByUpdatedAtDesc(userId).stream()
                .map(UserInterviewerRoleApplicationService::toResponse)
                .toList();
    }

    @Transactional
    public UserInterviewerRoleResponse create(
            String roleCode, String roleName, String interviewContent, String focusPoints, String evaluationHint) {
        String userId = BusinessRequestActor.requireUserId();
        String normalized = UserInterviewerRole.normalizeRoleCode(roleCode);
        repository
                .findByOwnerUserIdAndRoleCodeIgnoreCase(userId, normalized)
                .ifPresent(
                        x -> {
                            throw new DomainException("BUS-4091", "该角色代号已存在：" + x.getRoleCode());
                        });
        String id = shortIdGenerator.newId("ir");
        UserInterviewerRole r =
                UserInterviewerRole.createNew(id, userId, roleCode, roleName, interviewContent, focusPoints, evaluationHint);
        repository.insert(r);
        return toResponse(r);
    }

    @Transactional
    public UserInterviewerRoleResponse update(
            String roleId,
            String roleCode,
            String roleName,
            String interviewContent,
            String focusPoints,
            String evaluationHint) {
        String userId = BusinessRequestActor.requireUserId();
        UserInterviewerRole existing =
                repository
                        .findByIdAndOwnerUserId(roleId, userId)
                        .orElseThrow(() -> new ResourceNotFoundException("面试官角色不存在"));
        String normalized = UserInterviewerRole.normalizeRoleCode(roleCode);
        repository
                .findByOwnerUserIdAndRoleCodeIgnoreCase(userId, normalized)
                .filter(other -> !other.getRoleId().equals(roleId))
                .ifPresent(
                        x -> {
                            throw new DomainException("BUS-4091", "该角色代号已存在：" + x.getRoleCode());
                        });
        existing.updateProfile(roleCode, roleName, interviewContent, focusPoints, evaluationHint);
        repository.update(existing);
        return toResponse(existing);
    }

    @Transactional
    public void delete(String roleId) {
        String userId = BusinessRequestActor.requireUserId();
        if (repository.findByIdAndOwnerUserId(roleId, userId).isEmpty()) {
            throw new ResourceNotFoundException("面试官角色不存在");
        }
        repository.deleteByIdAndOwnerUserId(roleId, userId);
    }

    private static UserInterviewerRoleResponse toResponse(UserInterviewerRole r) {
        return new UserInterviewerRoleResponse(
                r.getRoleId(),
                r.getRoleCode(),
                r.getRoleName(),
                r.getInterviewContent(),
                r.getFocusPoints(),
                r.getEvaluationHint(),
                r.getCreatedAt(),
                r.getUpdatedAt());
    }
}
