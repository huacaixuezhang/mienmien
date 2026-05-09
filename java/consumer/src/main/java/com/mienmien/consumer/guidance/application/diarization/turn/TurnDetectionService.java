package com.mienmien.consumer.guidance.application.diarization.turn;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class TurnDetectionService {
    private final Map<String, String> activeSpeaker = new ConcurrentHashMap<>();
    private final TurnEventPublisher turnEventPublisher;

    public TurnDetectionService(TurnEventPublisher turnEventPublisher) {
        this.turnEventPublisher = turnEventPublisher;
    }

    public void onTranscriptionFrame(String sessionId, String speaker, String text, boolean partial, double confidence) {
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }
        String role = normalizeRole(speaker);
        String prev = activeSpeaker.get(sessionId);
        long ts = System.currentTimeMillis();
        if (!role.equals(prev)) {
            if ("INTERVIEWER".equals(role)) {
                publish(new TurnEvent(sessionId, TurnType.INTERVIEWER_QUESTION_START, role, text, ts, confidence));
            } else if ("CANDIDATE".equals(role)) {
                publish(new TurnEvent(sessionId, TurnType.CANDIDATE_ANSWER_START, role, text, ts, confidence));
            }
            activeSpeaker.put(sessionId, role);
        }
        if (!partial && looksSentenceEnd(text)) {
            if ("INTERVIEWER".equals(role)) {
                publish(new TurnEvent(sessionId, TurnType.INTERVIEWER_QUESTION_END, role, text, ts, confidence));
            } else if ("CANDIDATE".equals(role)) {
                publish(new TurnEvent(sessionId, TurnType.CANDIDATE_ANSWER_END, role, text, ts, confidence));
            }
        }
    }

    public void onManualQuestionRecorded(String sessionId, String source, String text) {
        String role = "text".equals(source) ? "CANDIDATE" : "INTERVIEWER";
        long ts = System.currentTimeMillis();
        if ("INTERVIEWER".equals(role)) {
            publish(new TurnEvent(sessionId, TurnType.INTERVIEWER_QUESTION_START, role, text, ts, 0.8d));
            publish(new TurnEvent(sessionId, TurnType.INTERVIEWER_QUESTION_END, role, text, ts + 1, 0.8d));
        } else {
            publish(new TurnEvent(sessionId, TurnType.CANDIDATE_ANSWER_START, role, text, ts, 0.8d));
            publish(new TurnEvent(sessionId, TurnType.CANDIDATE_ANSWER_END, role, text, ts + 1, 0.8d));
        }
    }

    public List<TurnEvent> toList(String sessionId) {
        return List.of();
    }

    private void publish(TurnEvent event) {
        turnEventPublisher.publish(event);
    }

    private String normalizeRole(String speaker) {
        if (speaker == null) {
            return "UNKNOWN";
        }
        return switch (speaker) {
            case "speaker_0", "INTERVIEWER" -> "INTERVIEWER";
            case "speaker_1", "CANDIDATE" -> "CANDIDATE";
            default -> "UNKNOWN";
        };
    }

    private boolean looksSentenceEnd(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        return text.endsWith("。") || text.endsWith("？") || text.endsWith("?") || text.endsWith("!") || text.endsWith("！");
    }

}
