package com.mienmien.business.management.jobposition.interfaces.rest;

import com.mienmien.business.management.application.dto.JobPositionResponse;
import com.mienmien.business.management.application.service.JobPositionApplicationService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/business/spaces/{spaceId}/job-positions")
public class JobPositionSpaceController {
    private final JobPositionApplicationService jobPositionApplicationService;

    public JobPositionSpaceController(JobPositionApplicationService jobPositionApplicationService) {
        this.jobPositionApplicationService = jobPositionApplicationService;
    }

    @PostMapping("/{positionId}/link")
    public JobPositionResponse linkToSpace(
            @PathVariable("spaceId") String spaceId,
            @PathVariable("positionId") String positionId) {
        return jobPositionApplicationService.linkToSpace(spaceId, positionId);
    }

    @DeleteMapping("/{positionId}/link")
    public JobPositionResponse unlinkFromSpace(
            @PathVariable("spaceId") String spaceId,
            @PathVariable("positionId") String positionId) {
        return jobPositionApplicationService.unlinkFromSpace(spaceId, positionId);
    }
}
