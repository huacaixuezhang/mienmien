package com.mienmien.business.management.interview.interfaces.rest;

import com.mienmien.business.management.application.dto.InterviewRecordResponse;
import com.mienmien.business.management.application.dto.VideoInterviewerSlotRequest;
import com.mienmien.business.management.application.dto.VideoInterviewSessionCreatedResponse;
import com.mienmien.business.management.application.service.InterviewApplicationService;
import com.mienmien.business.management.application.service.VideoInterviewSessionApplicationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
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
@RequestMapping("/api/v1/business/interviews")
public class InterviewController {
    private final InterviewApplicationService interviewApplicationService;
    private final VideoInterviewSessionApplicationService videoInterviewSessionApplicationService;

    public InterviewController(
            InterviewApplicationService interviewApplicationService,
            VideoInterviewSessionApplicationService videoInterviewSessionApplicationService) {
        this.interviewApplicationService = interviewApplicationService;
        this.videoInterviewSessionApplicationService = videoInterviewSessionApplicationService;
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
                req.summary() == null ? "" : req.summary(),
                req.positionId());
    }

    @GetMapping("/{spaceId}")
    public List<InterviewRecordResponse> listInterviews(@PathVariable("spaceId") String spaceId) {
        return interviewApplicationService.listBySpace(spaceId);
    }

    /**
     * 更新面试记录（与创建时 body 字段一致）；路径使用 {@code /records/} 前缀避免与按 spaceId 列表的 GET 冲突。
     */
    @PutMapping("/records/{recordId}")
    public InterviewRecordResponse updateInterview(
            @PathVariable("recordId") String recordId,
            @Valid @RequestBody CreateInterviewRequest req) {
        return interviewApplicationService.update(
                recordId,
                req.spaceId(),
                req.interviewType() == null ? "business" : req.interviewType(),
                req.round() == null ? 1 : req.round(),
                req.score() == null ? 0 : req.score(),
                req.result() == null ? "pending" : req.result(),
                req.summary() == null ? "" : req.summary(),
                req.positionId());
    }

    /**
     * 创建视频模拟面试运行时会话：固化简历/岗位/风格快照，返回 consumer WebSocket 路径供前端连接。
     */
    @PostMapping("/records/{recordId}/video-sessions")
    @ResponseStatus(HttpStatus.CREATED)
    public VideoInterviewSessionCreatedResponse createVideoInterviewSession(
            @PathVariable("recordId") String recordId, @Valid @RequestBody CreateVideoInterviewSessionRequest req) {
        return videoInterviewSessionApplicationService.create(
                recordId,
                req.spaceId(),
                req.roundIndex(),
                req.interviewerStyleKey(),
                req.interviewers() == null ? List.of() : req.interviewers());
    }

    public record CreateInterviewRequest(
            @NotBlank String spaceId,
            String interviewType,
            @Min(1) Integer round,
            Integer score,
            String result,
            String summary,
            /** 绑定岗位 ID；更新时省略或 null 表示保持原绑定，传空字符串表示清除 */
            String positionId) {
    }

    public record CreateVideoInterviewSessionRequest(
            @NotBlank String spaceId,
            @Min(0) int roundIndex,
            String interviewerStyleKey,
            List<VideoInterviewerSlotRequest> interviewers) {}
}
