package com.mienmien.business.management.resume.interfaces.rest;

import com.mienmien.business.management.application.dto.ResumeDocumentResponse;
import com.mienmien.business.management.application.dto.ResumeModuleDto;
import com.mienmien.business.management.application.service.ResumeDocumentApplicationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/business/spaces/{spaceId}/resume-documents")
public class ResumeDocumentController {
    private final ResumeDocumentApplicationService resumeDocumentApplicationService;

    public ResumeDocumentController(ResumeDocumentApplicationService resumeDocumentApplicationService) {
        this.resumeDocumentApplicationService = resumeDocumentApplicationService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ResumeDocumentResponse create(
            @PathVariable("spaceId") String spaceId,
            @Valid @RequestBody UpsertResumeDocumentRequest req) {
        return resumeDocumentApplicationService.create(spaceId, req.name(), req.modules());
    }

    @GetMapping
    public List<ResumeDocumentResponse> list(@PathVariable("spaceId") String spaceId) {
        return resumeDocumentApplicationService.listBySpace(spaceId);
    }

    @GetMapping("/{resumeId}")
    public ResumeDocumentResponse get(
            @PathVariable("spaceId") String spaceId,
            @PathVariable("resumeId") String resumeId) {
        return resumeDocumentApplicationService.get(spaceId, resumeId);
    }

    @PutMapping("/{resumeId}")
    public ResumeDocumentResponse update(
            @PathVariable("spaceId") String spaceId,
            @PathVariable("resumeId") String resumeId,
            @Valid @RequestBody UpsertResumeDocumentRequest req) {
        return resumeDocumentApplicationService.update(spaceId, resumeId, req.name(), req.modules());
    }

    @PostMapping("/{resumeId}/link")
    public ResumeDocumentResponse linkToSpace(
            @PathVariable("spaceId") String spaceId,
            @PathVariable("resumeId") String resumeId) {
        return resumeDocumentApplicationService.linkToSpace(spaceId, resumeId);
    }

    /**
     * 从该空间解除关联；若简历不再关联任何空间则删除主体数据。
     */
    @DeleteMapping("/{resumeId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @PathVariable("spaceId") String spaceId,
            @PathVariable("resumeId") String resumeId) {
        resumeDocumentApplicationService.deleteFromSpace(spaceId, resumeId);
    }

    public record UpsertResumeDocumentRequest(
            @NotBlank String name,
            @NotEmpty @Valid List<ResumeModuleDto> modules) {
    }
}
