import Foundation

public enum TurnEventType: String {
    case interviewerQuestionStart = "INTERVIEWER_QUESTION_START"
    case interviewerQuestionEnd = "INTERVIEWER_QUESTION_END"
    case candidateAnswerStart = "CANDIDATE_ANSWER_START"
    case candidateAnswerEnd = "CANDIDATE_ANSWER_END"

    public var displayLabel: String {
        switch self {
        case .interviewerQuestionStart: return "面试官开始提问"
        case .interviewerQuestionEnd: return "面试官结束提问"
        case .candidateAnswerStart: return "候选人开始回答"
        case .candidateAnswerEnd: return "候选人结束回答"
        }
    }
}

public struct RealtimeTranscriptionEvent {
    public let speaker: String
    public let text: String
    public let confidence: Double
}

public struct RealtimeTurnEvent {
    public let type: TurnEventType
    public let speaker: String
    public let text: String
}

public enum RealtimeInboundEvent {
    case transcription(RealtimeTranscriptionEvent)
    case turn(RealtimeTurnEvent)
    case other(String)
}

/// 与 Android `ConsumerApi` 对齐的最小 C 端 HTTP 客户端（演示用）。
public final class ConsumerHTTPClient {
    private let baseURL: URL
    private let session: URLSession
    private var wsTask: URLSessionWebSocketTask?

    public init(base: String = "http://127.0.0.1:8081/api/v1/consumer") {
        guard let u = URL(string: base) else {
            fatalError("invalid consumer base url")
        }
        baseURL = u
        let cfg = URLSessionConfiguration.ephemeral
        cfg.timeoutIntervalForRequest = 120
        session = URLSession(configuration: cfg)
    }

    public func createSession(completion: @escaping (Result<String, Error>) -> Void) {
        var req = URLRequest(url: baseURL.appendingPathComponent("sessions"))
        req.httpMethod = "POST"
        req.setValue("application/json", forHTTPHeaderField: "Content-Type")
        req.httpBody = Data(#"{"userId":"user_001","mode":"live"}"#.utf8)
        session.dataTask(with: req) { data, _, err in
            if let err {
                completion(.failure(err))
                return
            }
            let s = String(data: data ?? Data(), encoding: .utf8) ?? ""
            completion(.success(s))
        }.resume()
    }

    public func postTextEvent(sessionId: String, questionText: String, completion: @escaping (Result<String, Error>) -> Void) {
        let path = "sessions/\(sessionId)/events/text"
        var req = URLRequest(url: baseURL.appendingPathComponent(path))
        req.httpMethod = "POST"
        req.setValue("application/json", forHTTPHeaderField: "Content-Type")
        do {
            req.httpBody = try JSONSerialization.data(withJSONObject: ["questionText": questionText], options: [])
        } catch {
            completion(.failure(error))
            return
        }
        session.dataTask(with: req) { data, _, err in
            if let err {
                completion(.failure(err))
                return
            }
            completion(.success(String(data: data ?? Data(), encoding: .utf8) ?? ""))
        }.resume()
    }

    public func onceAnswer(sessionId: String, completion: @escaping (Result<String, Error>) -> Void) {
        let path = "sessions/\(sessionId)/answers/once"
        let req = URLRequest(url: baseURL.appendingPathComponent(path))
        session.dataTask(with: req) { data, _, err in
            if let err {
                completion(.failure(err))
                return
            }
            completion(.success(String(data: data ?? Data(), encoding: .utf8) ?? ""))
        }.resume()
    }

    public func photoQa(sessionId: String, completion: @escaping (Result<String, Error>) -> Void) {
        let path = "sessions/\(sessionId)/photo-qa"
        let req = URLRequest(url: baseURL.appendingPathComponent(path))
        session.dataTask(with: req) { data, _, err in
            if let err {
                completion(.failure(err))
                return
            }
            completion(.success(String(data: data ?? Data(), encoding: .utf8) ?? ""))
        }.resume()
    }

    @available(iOS 15.0, *)
    public func streamAnswerLines(sessionId: String) async throws -> String {
        let path = "sessions/\(sessionId)/answers/stream"
        var req = URLRequest(url: baseURL.appendingPathComponent(path))
        req.setValue("text/event-stream", forHTTPHeaderField: "Accept")
        let (bytes, _) = try await session.bytes(for: req)
        var acc = ""
        for try await line in bytes.lines {
            acc.append(line)
            acc.append("\n")
        }
        return acc
    }

    public func connectRealtimeDiarization(
        sessionId: String,
        mode: String = "unsupervised",
        onTextMessage: @escaping (String) -> Void
    ) {
        var comps = URLComponents(url: baseURL, resolvingAgainstBaseURL: false)
        comps?.scheme = "ws"
        comps?.path = "/ws/consumer/diarization"
        guard let wsURL = comps?.url else { return }
        let task = session.webSocketTask(with: wsURL)
        wsTask = task
        task.resume()
        let configJson = #"{"type":"config","mode":"\#(mode)","sessionId":"\#(sessionId)"}"#
        task.send(.string(configJson)) { _ in }
        receiveLoop(onTextMessage: onTextMessage)
    }

    public func connectRealtimeDiarization(
        sessionId: String,
        mode: String = "unsupervised",
        onEvent: @escaping (RealtimeInboundEvent) -> Void
    ) {
        connectRealtimeDiarization(sessionId: sessionId, mode: mode) { text in
            onEvent(Self.parseRealtimeEvent(text))
        }
    }

    public func sendPcmFrame(_ frame: Data) {
        wsTask?.send(.data(frame)) { _ in }
    }

    public func disconnectRealtimeDiarization() {
        wsTask?.cancel(with: .normalClosure, reason: nil)
        wsTask = nil
    }

    private func receiveLoop(onTextMessage: @escaping (String) -> Void) {
        wsTask?.receive { [weak self] result in
            switch result {
            case .success(let msg):
                switch msg {
                case .string(let text):
                    onTextMessage(text)
                case .data(let data):
                    onTextMessage(String(data: data, encoding: .utf8) ?? "")
                @unknown default:
                    break
                }
                self?.receiveLoop(onTextMessage: onTextMessage)
            case .failure:
                break
            }
        }
    }

    public static func parseRealtimeEvent(_ raw: String) -> RealtimeInboundEvent {
        guard let data = raw.data(using: .utf8),
              let obj = try? JSONSerialization.jsonObject(with: data) as? [String: Any]
        else {
            return .other(raw)
        }
        guard let type = obj["type"] as? String else {
            return .other(raw)
        }
        if type == "transcription" {
            return .transcription(RealtimeTranscriptionEvent(
                speaker: obj["speaker"] as? String ?? "",
                text: obj["text"] as? String ?? "",
                confidence: obj["confidence"] as? Double ?? 0
            ))
        }
        if let turnType = TurnEventType(rawValue: type) {
            return .turn(RealtimeTurnEvent(
                type: turnType,
                speaker: obj["speaker"] as? String ?? "",
                text: obj["text"] as? String ?? ""
            ))
        }
        if type == "turn_event",
           let turnRaw = obj["turnType"] as? String,
           let turnType = TurnEventType(rawValue: turnRaw) {
            return .turn(RealtimeTurnEvent(
                type: turnType,
                speaker: obj["speaker"] as? String ?? "",
                text: obj["text"] as? String ?? ""
            ))
        }
        return .other(raw)
    }
}
