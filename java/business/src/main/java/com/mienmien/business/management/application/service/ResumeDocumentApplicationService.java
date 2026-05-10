package com.mienmien.business.management.application.service;

import com.mienmien.business.management.application.dto.ResumeDocumentResponse;
import com.mienmien.business.management.application.dto.ResumeModuleDto;
import com.mienmien.business.management.application.security.BusinessRequestActor;
import com.mienmien.business.management.application.support.ApplicationSpaceGuard;
import com.mienmien.business.management.domain.DomainException;
import com.mienmien.business.management.domain.ResourceNotFoundException;
import com.mienmien.business.management.domain.model.ResumeDocument;
import com.mienmien.business.management.domain.model.ResumeModule;
import com.mienmien.business.management.domain.repository.ResumeDocumentRepository;
import com.mienmien.business.management.domain.repository.SpaceRepository;
import com.mienmien.business.management.domain.support.ShortIdGenerator;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ResumeDocumentApplicationService {
    private final ResumeDocumentRepository resumeDocumentRepository;
    private final SpaceRepository spaceRepository;
    private final ShortIdGenerator shortIdGenerator;

    public ResumeDocumentApplicationService(
            ResumeDocumentRepository resumeDocumentRepository,
            SpaceRepository spaceRepository,
            ShortIdGenerator shortIdGenerator) {
        this.resumeDocumentRepository = resumeDocumentRepository;
        this.spaceRepository = spaceRepository;
        this.shortIdGenerator = shortIdGenerator;
    }

    public ResumeDocumentResponse create(String spaceId, String name, List<ResumeModuleDto> moduleDtos) {
        if (spaceId != null && !spaceId.isBlank()) {
            ApplicationSpaceGuard.requireOwnedActiveSpace(spaceId, spaceRepository);
        }
        String userId = BusinessRequestActor.requireUserId();
        String resumeId = shortIdGenerator.newId("r");
        List<ResumeModule> modules = toModules(moduleDtos);
        ResumeDocument doc = ResumeDocument.createNew(resumeId, userId, name, modules);
        resumeDocumentRepository.save(doc);
        if (spaceId != null && !spaceId.isBlank()) {
            resumeDocumentRepository.addSpaceLink(resumeId, spaceId);
        }
        return toResponse(doc, resumeDocumentRepository.findSpaceIdsByResumeId(resumeId));
    }

    public List<ResumeDocumentResponse> listBySpace(String spaceId) {
        ApplicationSpaceGuard.requireOwnedActiveSpace(spaceId, spaceRepository);
        return resumeDocumentRepository.findBySpaceIdOrderByUpdatedAtDesc(spaceId).stream()
                .map(d -> toResponse(d, resumeDocumentRepository.findSpaceIdsByResumeId(d.getResumeId())))
                .toList();
    }

    /** 当前用户全部简历（每份一条），含所有关联空间 id */
    public List<ResumeDocumentResponse> listMine() {
        String userId = BusinessRequestActor.requireUserId();
        return resumeDocumentRepository.findByUserIdOrderByUpdatedAtDesc(userId).stream()
                .map(d -> toResponse(d, resumeDocumentRepository.findSpaceIdsByResumeId(d.getResumeId())))
                .toList();
    }

    public ResumeDocumentResponse get(String spaceId, String resumeId) {
        ApplicationSpaceGuard.requireOwnedActiveSpace(spaceId, spaceRepository);
        ResumeDocument doc = resumeDocumentRepository.findByResumeId(resumeId)
                .orElseThrow(() -> new ResourceNotFoundException("简历不存在"));
        assertActorOwnsResume(doc);
        List<String> linked = resumeDocumentRepository.findSpaceIdsByResumeId(resumeId);
        if (!linked.isEmpty()) {
            requireLinked(spaceId, resumeId);
        }
        return toResponse(doc, linked);
    }

    /** 不校验 spaceId 路径，仅校验归属用户；用于聚合列表进入编辑 */
    public ResumeDocumentResponse getForOwner(String resumeId) {
        ResumeDocument doc = resumeDocumentRepository.findByResumeId(resumeId)
                .orElseThrow(() -> new ResourceNotFoundException("简历不存在"));
        assertActorOwnsResume(doc);
        return toResponse(doc, resumeDocumentRepository.findSpaceIdsByResumeId(resumeId));
    }

    public ResumeDocumentResponse update(String spaceId, String resumeId, String name, List<ResumeModuleDto> moduleDtos) {
        if (spaceId != null && !spaceId.isBlank()) {
            ApplicationSpaceGuard.requireOwnedActiveSpace(spaceId, spaceRepository);
        }
        ResumeDocument doc = resumeDocumentRepository.findByResumeId(resumeId)
                .orElseThrow(() -> new ResourceNotFoundException("简历不存在"));
        assertActorOwnsResume(doc);
        List<String> linked = resumeDocumentRepository.findSpaceIdsByResumeId(resumeId);
        if (!linked.isEmpty()) {
            if (spaceId == null || spaceId.isBlank()) {
                throw new DomainException("BUS-4001", "已关联空间的简历更新时需指定空间");
            }
            requireLinked(spaceId, resumeId);
        }
        doc.rename(name);
        doc.replaceModules(toModules(moduleDtos));
        resumeDocumentRepository.save(doc);
        return toResponse(doc, resumeDocumentRepository.findSpaceIdsByResumeId(resumeId));
    }

    /** 不依赖空间路径，仅校验归属用户（适用于尚未关联任何空间的简历） */
    public ResumeDocumentResponse updateForOwner(String resumeId, String name, List<ResumeModuleDto> moduleDtos) {
        ResumeDocument doc = resumeDocumentRepository.findByResumeId(resumeId)
                .orElseThrow(() -> new ResourceNotFoundException("简历不存在"));
        assertActorOwnsResume(doc);
        doc.rename(name);
        doc.replaceModules(toModules(moduleDtos));
        resumeDocumentRepository.save(doc);
        return toResponse(doc, resumeDocumentRepository.findSpaceIdsByResumeId(resumeId));
    }

    /**
     * 从指定空间解除关联；若已无关联空间则删除简历主体。
     */
    public void deleteFromSpace(String spaceId, String resumeId) {
        ApplicationSpaceGuard.requireOwnedActiveSpace(spaceId, spaceRepository);
        ResumeDocument doc = resumeDocumentRepository.findByResumeId(resumeId)
                .orElseThrow(() -> new ResourceNotFoundException("简历不存在"));
        assertActorOwnsResume(doc);
        requireLinked(spaceId, resumeId);
        resumeDocumentRepository.deleteSpaceLink(resumeId, spaceId);
        if (resumeDocumentRepository.countSpaceLinks(resumeId) == 0) {
            resumeDocumentRepository.deleteByResumeId(resumeId);
        }
    }

    /** 删除简历及其全部空间关联 */
    public void deleteEntire(String resumeId) {
        ResumeDocument doc = resumeDocumentRepository.findByResumeId(resumeId)
                .orElseThrow(() -> new ResourceNotFoundException("简历不存在"));
        assertActorOwnsResume(doc);
        resumeDocumentRepository.deleteByResumeId(resumeId);
    }

    /** 将已有简历关联到目标空间（幂等） */
    public ResumeDocumentResponse linkToSpace(String spaceId, String resumeId) {
        ApplicationSpaceGuard.requireOwnedActiveSpace(spaceId, spaceRepository);
        ResumeDocument doc = resumeDocumentRepository.findByResumeId(resumeId)
                .orElseThrow(() -> new ResourceNotFoundException("简历不存在"));
        assertActorOwnsResume(doc);
        resumeDocumentRepository.addSpaceLink(resumeId, spaceId);
        return toResponse(doc, resumeDocumentRepository.findSpaceIdsByResumeId(resumeId));
    }

    private void requireLinked(String spaceId, String resumeId) {
        List<String> ids = resumeDocumentRepository.findSpaceIdsByResumeId(resumeId);
        if (ids.stream().noneMatch(s -> s.equals(spaceId))) {
            throw new ResourceNotFoundException("简历不存在或未关联到该空间");
        }
    }

    private void assertActorOwnsResume(ResumeDocument doc) {
        String actor = BusinessRequestActor.requireUserId();
        if (!doc.getUserId().equals(actor)) {
            throw new DomainException("BUS-4033", "无权操作该简历");
        }
    }

    private static List<ResumeModule> toModules(List<ResumeModuleDto> dtos) {
        if (dtos == null || dtos.isEmpty()) {
            throw new DomainException("BUS-4001", "至少包含一个模块");
        }
        return dtos.stream()
                .map(d -> new ResumeModule(d.id(), d.title(), d.text()))
                .toList();
    }

    private static ResumeDocumentResponse toResponse(ResumeDocument d, List<String> spaceIds) {
        List<ResumeModuleDto> modules = d.getModulesView().stream()
                .map(m -> new ResumeModuleDto(m.id(), m.title(), m.text()))
                .toList();
        String firstSpace = spaceIds != null && !spaceIds.isEmpty() ? spaceIds.get(0) : "";
        return new ResumeDocumentResponse(
                d.getResumeId(),
                firstSpace,
                spaceIds == null ? List.of() : List.copyOf(spaceIds),
                d.getUserId(),
                d.getName(),
                modules,
                d.getCreatedAt(),
                d.getUpdatedAt()
        );
    }
}
