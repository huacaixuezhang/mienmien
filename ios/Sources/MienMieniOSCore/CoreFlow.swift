import Foundation

public enum CFeature: String {
    case mockInterview = "模拟面试"
    case realtimeGuidance = "实时指导"
    case photoQA = "拍照问答"
}

public struct CoreFlow {
    public init() {}

    public func supportedFeatures() -> [String] {
        return [CFeature.mockInterview.rawValue, CFeature.realtimeGuidance.rawValue, CFeature.photoQA.rawValue]
    }

    public func consumerBaseURL() -> URL {
        return URL(string: "http://localhost:8081/api/v1/consumer")!
    }

    public func createSessionPayload() -> [String: String] {
        return ["userId": "user_001", "mode": "live"]
    }

    /// 与 Android `MainActivity` 相同的主流程：建会话 → 文本问题 →（可选）SSE。
    public func demoStepsDescription() -> [String] {
        return [
            "创建会话 POST /sessions",
            "发送文本问题 POST /sessions/{id}/events/text",
            "实时语音 WS /ws/consumer/diarization（PCM 16k 单通道）",
            "流式回答 GET /sessions/{id}/answers/stream（iOS 15+ 使用 ConsumerHTTPClient.streamAnswerLines）",
            "降级回答 GET /sessions/{id}/answers/once"
        ]
    }
}
