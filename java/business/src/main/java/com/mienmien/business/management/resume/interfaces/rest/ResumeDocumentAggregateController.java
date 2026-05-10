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

/**
 * 跨空间维度的简历接口：一份数据多空间关联；创建时可不传 spaceId（不绑定空间）。
 */
@RestController
@RequestMapping("/api/v1/business/resume-documents")
public class ResumeDocumentAggregateController {
    private final ResumeDocumentApplicationService resumeDocumentApplicationService;

    public ResumeDocumentAggregateController(ResumeDocumentApplicationService resumeDocumentApplicationService) {
        this.resumeDocumentApplicationService = resumeDocumentApplicationService;
    }

    @GetMapping
    public List<ResumeDocumentResponse> listMine() {
        return resumeDocumentApplicationService.listMine();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ResumeDocumentResponse createMine(@Valid @RequestBody CreateResumeDocumentRequest req) {
        return resumeDocumentApplicationService.create(req.spaceId(), req.name(), req.modules());
    }

    @GetMapping("/{resumeId}")
    public ResumeDocumentResponse get(@PathVariable("resumeId") String resumeId) {
        return resumeDocumentApplicationService.getForOwner(resumeId);
    }

    @PutMapping("/{resumeId}")
    public ResumeDocumentResponse updateMine(
            @PathVariable("resumeId") String resumeId,
            @Valid @RequestBody UpsertResumeDocumentBody req) {
        return resumeDocumentApplicationService.updateForOwner(resumeId, req.name(), req.modules());
    }

    @DeleteMapping("/{resumeId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteEntire(@PathVariable("resumeId") String resumeId) {
        resumeDocumentApplicationService.deleteEntire(resumeId);
    }

    public record CreateResumeDocumentRequest(
            String spaceId,
            @NotBlank String name,
            @NotEmpty @Valid List<ResumeModuleDto> modules) {
    }

    public record UpsertResumeDocumentBody(
            @NotBlank String name,
            @NotEmpty @Valid List<ResumeModuleDto> modules) {
    }
}
