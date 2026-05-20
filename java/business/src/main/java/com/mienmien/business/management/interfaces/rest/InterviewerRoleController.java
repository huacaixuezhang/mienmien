package com.mienmien.business.management.interfaces.rest;

import com.mienmien.business.management.application.dto.UserInterviewerRoleResponse;
import com.mienmien.business.management.application.service.UserInterviewerRoleApplicationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
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

@RestController
@RequestMapping("/api/v1/business/interviewer-roles")
public class InterviewerRoleController {

    private final UserInterviewerRoleApplicationService userInterviewerRoleApplicationService;

    public InterviewerRoleController(UserInterviewerRoleApplicationService userInterviewerRoleApplicationService) {
        this.userInterviewerRoleApplicationService = userInterviewerRoleApplicationService;
    }

    @GetMapping
    public List<UserInterviewerRoleResponse> list() {
        return userInterviewerRoleApplicationService.listMine();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserInterviewerRoleResponse create(@Valid @RequestBody UpsertInterviewerRoleRequest req) {
        return userInterviewerRoleApplicationService.create(
                req.roleCode(), req.roleName(), req.interviewContent(), req.focusPoints(), req.evaluationHint());
    }

    @PutMapping("/{roleId}")
    public UserInterviewerRoleResponse update(
            @PathVariable("roleId") String roleId, @Valid @RequestBody UpsertInterviewerRoleRequest req) {
        return userInterviewerRoleApplicationService.update(
                roleId,
                req.roleCode(),
                req.roleName(),
                req.interviewContent(),
                req.focusPoints(),
                req.evaluationHint());
    }

    @DeleteMapping("/{roleId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable("roleId") String roleId) {
        userInterviewerRoleApplicationService.delete(roleId);
    }

    public record UpsertInterviewerRoleRequest(
            @NotBlank String roleCode,
            @NotBlank String roleName,
            @NotBlank String interviewContent,
            @NotBlank String focusPoints,
            String evaluationHint) {}
}
