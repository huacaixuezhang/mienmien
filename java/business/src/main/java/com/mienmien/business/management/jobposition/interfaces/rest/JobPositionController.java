package com.mienmien.business.management.jobposition.interfaces.rest;

import com.mienmien.business.management.application.dto.JobPositionJdParseResponse;
import com.mienmien.business.management.application.dto.JobPositionResponse;
import com.mienmien.business.management.application.service.JobPositionApplicationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/v1/business/job-positions")
public class JobPositionController {
    private final JobPositionApplicationService jobPositionApplicationService;

    public JobPositionController(JobPositionApplicationService jobPositionApplicationService) {
        this.jobPositionApplicationService = jobPositionApplicationService;
    }

    /** 当前用户全部岗位（每份一条，含 spaceIds） */
    @GetMapping
    public List<JobPositionResponse> listMine() {
        return jobPositionApplicationService.listMine();
    }

    @GetMapping("/item/{positionId}")
    public JobPositionResponse getJobPosition(@PathVariable("positionId") String positionId) {
        return jobPositionApplicationService.getForOwner(positionId);
    }

    @GetMapping("/by-space/{spaceId}")
    public List<JobPositionResponse> listJobPositions(@PathVariable("spaceId") String spaceId) {
        return jobPositionApplicationService.listBySpace(spaceId);
    }

    /** 服务端调用用户已配置的模型，将 JD 全文解析为岗位字段 JSON（与前端弹窗字段一致）。 */
    @PostMapping("/parse-jd")
    public JobPositionJdParseResponse parseJd(@Valid @RequestBody ParseJdRequest req) {
        return jobPositionApplicationService.parseJdToStructuredFields(req.rawText());
    }

    /** 与 {@code POST /jd-targets/parse-job-position-image} 等价：multipart 字段名 {@code image}。 */
    @PostMapping(value = "/parse-jd-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public JobPositionJdParseResponse parseJdImage(@RequestPart("image") MultipartFile image) {
        return jobPositionApplicationService.parseJdImageToStructuredFields(image);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public JobPositionResponse createJobPosition(@Valid @RequestBody UpsertJobPositionRequest req) {
        return jobPositionApplicationService.create(
                req.spaceId(),
                req.title(),
                req.company(),
                req.location(),
                req.baseRange()
        );
    }

    @PutMapping("/item/{positionId}")
    public JobPositionResponse updateJobPosition(@PathVariable("positionId") String positionId, @Valid @RequestBody UpdateJobPositionRequest req) {
        return jobPositionApplicationService.update(positionId, req.title(), req.company(), req.location(), req.baseRange());
    }

    @DeleteMapping("/item/{positionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteJobPosition(@PathVariable("positionId") String positionId) {
        jobPositionApplicationService.deleteEntire(positionId);
    }

    public record ParseJdRequest(@NotBlank String rawText) {
    }

    public record UpsertJobPositionRequest(
            String spaceId,
            @NotBlank String title,
            String company,
            String location,
            String baseRange) {
    }

    public record UpdateJobPositionRequest(
            @NotBlank String title,
            String company,
            String location,
            String baseRange) {
    }
}
