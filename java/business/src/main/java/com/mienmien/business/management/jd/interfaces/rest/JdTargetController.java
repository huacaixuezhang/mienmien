package com.mienmien.business.management.jd.interfaces.rest;

import com.mienmien.business.management.application.dto.JobPositionJdParseResponse;
import com.mienmien.business.management.application.dto.JdTargetResponse;
import com.mienmien.business.management.application.service.JdTargetApplicationService;
import com.mienmien.business.management.application.service.JobPositionApplicationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/v1/business/jd-targets")
public class JdTargetController {
    private final JdTargetApplicationService jdTargetApplicationService;
    private final JobPositionApplicationService jobPositionApplicationService;

    public JdTargetController(
            JdTargetApplicationService jdTargetApplicationService,
            JobPositionApplicationService jobPositionApplicationService) {
        this.jdTargetApplicationService = jdTargetApplicationService;
        this.jobPositionApplicationService = jobPositionApplicationService;
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

    /**
     * 与 {@code POST /job-positions/parse-jd} 等价：从 JD 全文解析岗位结构化字段（服务端调模型返回 JSON DTO）。便于与既有
     * {@code /jd-targets/*} 路由一同部署、排查 404。
     */
    @PostMapping("/parse-job-position")
    public JobPositionJdParseResponse parseJobPositionFromJd(@Valid @RequestBody ParseJobFromJdRequest req) {
        return jobPositionApplicationService.parseJdToStructuredFields(req.rawText());
    }

    /**
     * 从岗位 JD 截图解析结构化字段；multipart 字段名为 {@code image}。需 OpenAI 兼容 chat/completions + 控制台支持图片的多模态模型（如
     * qwen3.5-plus、qwen-vl-plus，以官方为准）。
     */
    @PostMapping(value = "/parse-job-position-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public JobPositionJdParseResponse parseJobPositionFromImage(@RequestPart("image") MultipartFile image) {
        return jobPositionApplicationService.parseJdImageToStructuredFields(image);
    }

    public record ParseJobFromJdRequest(@NotBlank String rawText) {
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
