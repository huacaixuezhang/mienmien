package com.mienmien.business.management.interfaces.rest;

import com.mienmien.business.management.application.dto.UserInterviewerStyleResponse;
import com.mienmien.business.management.application.service.UserInterviewerStyleApplicationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
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
@RequestMapping("/api/v1/business/interviewer-styles")
public class InterviewerStyleController {

    private final UserInterviewerStyleApplicationService userInterviewerStyleApplicationService;

    public InterviewerStyleController(UserInterviewerStyleApplicationService userInterviewerStyleApplicationService) {
        this.userInterviewerStyleApplicationService = userInterviewerStyleApplicationService;
    }

    @GetMapping
    public List<UserInterviewerStyleResponse> list() {
        return userInterviewerStyleApplicationService.listMine();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserInterviewerStyleResponse create(@Valid @RequestBody UpsertInterviewerStyleRequest req) {
        return userInterviewerStyleApplicationService.create(req.title(), req.promptBody());
    }

    @PutMapping("/{styleId}")
    public UserInterviewerStyleResponse update(
            @PathVariable("styleId") String styleId, @Valid @RequestBody UpsertInterviewerStyleRequest req) {
        return userInterviewerStyleApplicationService.update(styleId, req.title(), req.promptBody());
    }

    @DeleteMapping("/{styleId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable("styleId") String styleId) {
        userInterviewerStyleApplicationService.delete(styleId);
    }

    public record UpsertInterviewerStyleRequest(@NotBlank String title, @NotBlank String promptBody) {}
}
