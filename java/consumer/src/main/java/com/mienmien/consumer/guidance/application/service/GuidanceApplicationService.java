package com.mienmien.consumer.guidance.application.service;

import com.mienmien.consumer.guidance.application.dto.*;
import com.mienmien.consumer.guidance.application.diarization.turn.TurnDetectionService;
import com.mienmien.consumer.guidance.application.port.out.AnswerGenerator;
import com.mienmien.consumer.guidance.domain.DomainException;
import com.mienmien.consumer.guidance.domain.ResourceNotFoundException;
import com.mienmien.consumer.guidance.domain.capability.ClientQuestionEnrichmentPolicy;
import com.mienmien.consumer.guidance.domain.model.GuidanceSession;
import com.mienmien.consumer.guidance.domain.model.QuestionEvent;
import com.mienmien.consumer.guidance.domain.repository.AnswerStreamRepository;
import com.mienmien.consumer.guidance.domain.repository.GuidanceSessionRepository;
import com.mienmien.consumer.guidance.domain.repository.QuestionEventRepository;
import com.mienmien.consumer.guidance.domain.support.ShortIdGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class GuidanceApplicationService {
    private static final Logger log = LoggerFactory.getLogger(GuidanceApplicationService.class);

    private final GuidanceSessionRepository sessionRepository;
    private final QuestionEventRepository questionEventRepository;
    private final AnswerStreamRepository answerStreamRepository;
    private final ShortIdGenerator idGenerator;
    private final ClientQuestionEnrichmentPolicy questionEnrichmentPolicy;
    private final TurnDetectionService turnDetectionService;
    private final AnswerGenerator answerGenerator;

    public GuidanceApplicationService(
            GuidanceSessionRepository sessionRepository,
            QuestionEventRepository questionEventRepository,
            AnswerStreamRepository answerStreamRepository,
            ShortIdGenerator idGenerator,
            ClientQuestionEnrichmentPolicy questionEnrichmentPolicy,
            TurnDetectionService turnDetectionService,
            AnswerGenerator answerGenerator) {
        this.sessionRepository = sessionRepository;
        this.questionEventRepository = questionEventRepository;
        this.answerStreamRepository = answerStreamRepository;
        this.idGenerator = idGenerator;
        this.questionEnrichmentPolicy = questionEnrichmentPolicy;
        this.turnDetectionService = turnDetectionService;
        this.answerGenerator = answerGenerator;
    }

    @Transactional
    public SessionCreatedResponse createSession(String userId, String mode) {
        GuidanceSession session = GuidanceSession.createNew(idGenerator.newId("gs_"), userId, mode);
        sessionRepository.save(session);
        log.info("guidance.session.created sessionId={} userId={} mode={}", session.getSessionId(), session.getUserId(),
                session.getMode());
        return SessionCreatedResponse.from(session);
    }

    @Transactional
    public QuestionRecordedResponse recordQuestion(String sessionId, String source, String questionText) {
        requireOpenSession(sessionId);
        String resolved = questionText == null ? "" : questionText;
        if ("voice".equals(source)) {
            resolved = questionEnrichmentPolicy.enrichVoiceQuestion(sessionId, resolved);
        } else if ("photo".equals(source)) {
            resolved = questionEnrichmentPolicy.enrichPhotoQuestion(sessionId, resolved);
        }
        QuestionEvent event = QuestionEvent.create(
                idGenerator.newId("qe_"),
                sessionId,
                source,
                resolved
        );
        questionEventRepository.save(event);
        sessionRepository.updateStatus(sessionId, "listening");
        turnDetectionService.onManualQuestionRecorded(sessionId, source, resolved);
        log.info("guidance.question.recorded sessionId={} questionEventId={} source={}", sessionId, event.getEventId(),
                source);
        return QuestionRecordedResponse.from(event);
    }

    @Transactional(readOnly = true)
    public PhotoQaResponse photoQa(String sessionId) {
        requireSession(sessionId);
        return new PhotoQaResponse(sessionId, answerGenerator.photoQaAdvisory(), "replaceable-adapter");
    }

    @Transactional(readOnly = true)
    public OnceAnswerResponse onceAnswer(String sessionId) {
        requireSession(sessionId);
        return new OnceAnswerResponse(sessionId, answerGenerator.onceAnswer(), "fallback-once");
    }

    /**
     * 读取最新问题、落库最终回答摘要，并返回应按序推送的文本块。
     */
    @Transactional
    public StreamedAnswerPlan prepareStreamedAnswer(String sessionId) {
        requireOpenSession(sessionId);
        sessionRepository.updateStatus(sessionId, "analyzing");
        String question = questionEventRepository.findLatestQuestionText(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("会话中尚无问题事件: " + sessionId));
        String questionEventId = questionEventRepository.findLatestQuestionEventId(sessionId).orElse("-");

        List<String> chunks = new ArrayList<>(answerGenerator.progressiveChunks());
        String finalAnswer = answerGenerator.composeFinalAnswer(question);
        String streamId = idGenerator.newId("as_");
        answerStreamRepository.saveWithLatestEvent(streamId, sessionId, finalAnswer);
        chunks.add(finalAnswer);
        sessionRepository.updateStatus(sessionId, "completed");
        log.info("guidance.answer.prepared sessionId={} questionEventId={} streamId={}", sessionId, questionEventId,
                streamId);
        return new StreamedAnswerPlan(chunks);
    }

    @Transactional
    public void markStreamAnswerFailedIfRecoverable(String sessionId) {
        sessionRepository.findById(sessionId).ifPresent(s -> {
            if (s.isClosed()) {
                return;
            }
            sessionRepository.updateStatus(sessionId, "failed");
            log.warn("guidance.answer.stream_failed sessionId={}", sessionId);
        });
    }

    @Transactional
    public SessionEndedResponse endSession(String sessionId) {
        GuidanceSession s = requireSession(sessionId);
        if (s.isClosed()) {
            return new SessionEndedResponse(
                    s.getSessionId(),
                    s.getStatus(),
                    s.getEndedAt() == null ? "" : s.getEndedAt().toString()
            );
        }
        Instant ended = Instant.now();
        sessionRepository.endSession(sessionId, ended);
        log.info("guidance.session.ended sessionId={} endedAt={}", sessionId, ended);
        return new SessionEndedResponse(sessionId, "completed", ended.toString());
    }

    private GuidanceSession requireSession(String sessionId) {
        return sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("会话不存在: " + sessionId));
    }

    private GuidanceSession requireOpenSession(String sessionId) {
        GuidanceSession s = requireSession(sessionId);
        if (s.isClosed()) {
            throw new DomainException("CON-4091", "会话已结束，无法继续操作");
        }
        return s;
    }

    public String streamFallbackMessage() {
        return answerGenerator.streamFallbackMessage();
    }
}
