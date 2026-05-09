package com.mienmien.consumer.guidance.interfaces.rest;

import com.mienmien.consumer.config.ConsumerRuntimeProperties;
import com.mienmien.consumer.guidance.application.dto.*;
import com.mienmien.consumer.guidance.application.service.AnswerStreamingUseCase;
import com.mienmien.consumer.guidance.application.service.GuidanceApplicationService;
import com.mienmien.consumer.guidance.interfaces.sse.SseTurnEventPublisher;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/consumer")
public class ConsumerController {
    private final GuidanceApplicationService guidanceApplicationService;
    private final AnswerStreamingUseCase answerStreamingUseCase;
    private final ConsumerRuntimeProperties consumerRuntimeProperties;
    private final SseTurnEventPublisher sseTurnEventPublisher;

    public ConsumerController(
            GuidanceApplicationService guidanceApplicationService,
            AnswerStreamingUseCase answerStreamingUseCase,
            ConsumerRuntimeProperties consumerRuntimeProperties,
            SseTurnEventPublisher sseTurnEventPublisher) {
        this.guidanceApplicationService = guidanceApplicationService;
        this.answerStreamingUseCase = answerStreamingUseCase;
        this.consumerRuntimeProperties = consumerRuntimeProperties;
        this.sseTurnEventPublisher = sseTurnEventPublisher;
    }

    @GetMapping("/health/stream")
    public ResponseEntity<Map<String, String>> streamHealth() {
        if (consumerRuntimeProperties.isStreamDegraded()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("code", "CON-5031", "message", "流式回答服务暂不可用（降级）"));
        }
        return ResponseEntity.ok(Map.of("status", "UP", "stream", "AVAILABLE"));
    }

    @PostMapping("/sessions")
    @ResponseStatus(HttpStatus.CREATED)
    public SessionCreatedResponse createSession(@RequestBody CreateSessionRequest req) {
        return guidanceApplicationService.createSession(req.userId(), req.mode());
    }

    @PostMapping("/sessions/{sessionId}/end")
    public SessionEndedResponse endSession(@PathVariable String sessionId) {
        return guidanceApplicationService.endSession(sessionId);
    }

    @PostMapping("/sessions/{sessionId}/events/voice")
    public QuestionRecordedResponse voiceEvent(
            @PathVariable String sessionId,
            @Valid @RequestBody QuestionEventRequest req) {
        return guidanceApplicationService.recordQuestion(
                sessionId,
                "voice",
                req.questionText()
        );
    }

    @PostMapping("/sessions/{sessionId}/events/photo")
    public QuestionRecordedResponse photoEvent(
            @PathVariable String sessionId,
            @Valid @RequestBody QuestionEventRequest req) {
        return guidanceApplicationService.recordQuestion(
                sessionId,
                "photo",
                req.questionText()
        );
    }

    @PostMapping("/sessions/{sessionId}/events/text")
    public QuestionRecordedResponse textEvent(
            @PathVariable String sessionId,
            @Valid @RequestBody QuestionEventRequest req) {
        return guidanceApplicationService.recordQuestion(
                sessionId,
                "text",
                req.questionText()
        );
    }

    @GetMapping("/sessions/{sessionId}/photo-qa")
    public PhotoQaResponse photoQa(@PathVariable String sessionId) {
        return guidanceApplicationService.photoQa(sessionId);
    }

    @GetMapping(value = "/sessions/{sessionId}/answers/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamAnswer(@PathVariable String sessionId) {
        return answerStreamingUseCase.streamAnswer(sessionId);
    }

    @GetMapping("/sessions/{sessionId}/answers/once")
    public OnceAnswerResponse onceAnswer(@PathVariable String sessionId) {
        return guidanceApplicationService.onceAnswer(sessionId);
    }

    @GetMapping(value = "/sessions/{sessionId}/turns/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamTurns(@PathVariable String sessionId) {
        return sseTurnEventPublisher.subscribe(sessionId);
    }

    public record CreateSessionRequest(String userId, String mode) {
    }

    public record QuestionEventRequest(@NotBlank String questionText) {
    }
}
