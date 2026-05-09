package com.mienmien.business.management.resume.interfaces.rest;

import com.mienmien.business.management.application.dto.ResumeResponse;
import com.mienmien.business.management.application.service.ResumeApplicationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/business/resumes")
public class ResumeController {
    private final ResumeApplicationService resumeApplicationService;

    public ResumeController(ResumeApplicationService resumeApplicationService) {
        this.resumeApplicationService = resumeApplicationService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ResumeResponse createResume(@Valid @RequestBody CreateResumeRequest req) {
        return resumeApplicationService.createResume(req.spaceId(), req.content(), req.version() == null ? 1 : req.version());
    }

    @GetMapping("/{spaceId}")
    public List<ResumeResponse> listResumes(@PathVariable("spaceId") String spaceId) {
        return resumeApplicationService.listBySpace(spaceId);
    }

    public record CreateResumeRequest(
            @NotBlank String spaceId,
            String content,
            @Min(1) Integer version) {
    }
}
