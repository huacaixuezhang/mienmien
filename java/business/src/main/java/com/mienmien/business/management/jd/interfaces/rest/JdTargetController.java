package com.mienmien.business.management.jd.interfaces.rest;

import com.mienmien.business.management.application.dto.JdTargetResponse;
import com.mienmien.business.management.application.service.JdTargetApplicationService;
import jakarta.validation.Valid;
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
@RequestMapping("/api/v1/business/jd-targets")
public class JdTargetController {
    private final JdTargetApplicationService jdTargetApplicationService;

    public JdTargetController(JdTargetApplicationService jdTargetApplicationService) {
        this.jdTargetApplicationService = jdTargetApplicationService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public JdTargetResponse createJd(@Valid @RequestBody CreateJdTargetRequest req) {
        return jdTargetApplicationService.create(req.spaceId(), req.rawText(), req.focusPoints());
    }

    @GetMapping("/{spaceId}")
    public List<JdTargetResponse> listJd(@PathVariable("spaceId") String spaceId) {
        return jdTargetApplicationService.listBySpace(spaceId);
    }

    @PostMapping("/analyze")
    public AnalyzeResponse analyze(@Valid @RequestBody AnalyzeRequest req) {
        return new AnalyzeResponse(jdTargetApplicationService.analyzeFocusPoints(req.rawText()));
    }

    public record CreateJdTargetRequest(
            @NotBlank String spaceId,
            String rawText,
            String focusPoints) {
    }

    public record AnalyzeRequest(@NotBlank String rawText) {
    }

    public record AnalyzeResponse(String focusPoints) {
    }
}
