package com.mienmien.business.management.interview.interfaces.rest;

import com.mienmien.business.management.application.dto.InterviewRecordResponse;
import com.mienmien.business.management.application.service.InterviewApplicationService;
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
@RequestMapping("/api/v1/business/interviews")
public class InterviewController {
    private final InterviewApplicationService interviewApplicationService;

    public InterviewController(InterviewApplicationService interviewApplicationService) {
        this.interviewApplicationService = interviewApplicationService;
    }

    @PostMapping("/{type}")
    @ResponseStatus(HttpStatus.CREATED)
    public InterviewRecordResponse createInterview(@PathVariable("type") String type, @Valid @RequestBody CreateInterviewRequest req) {
        return interviewApplicationService.create(
                type,
                req.spaceId(),
                req.interviewType() == null ? "business" : req.interviewType(),
                req.round() == null ? 1 : req.round(),
                req.score() == null ? 0 : req.score(),
                req.result() == null ? "pending" : req.result(),
                req.summary() == null ? "" : req.summary()
        );
    }

    @GetMapping("/{spaceId}")
    public List<InterviewRecordResponse> listInterviews(@PathVariable("spaceId") String spaceId) {
        return interviewApplicationService.listBySpace(spaceId);
    }

    public record CreateInterviewRequest(
            @NotBlank String spaceId,
            String interviewType,
            @Min(1) Integer round,
            Integer score,
            String result,
            String summary) {
    }
}
