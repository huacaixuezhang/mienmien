import Foundation
import AVFoundation

/// iOS 端实时语音采集：单通道 PCM 16k/16bit，分帧回调给上层发送 WebSocket。
public final class RealtimeAudioStreamer {
    private let engine = AVAudioEngine()
    private let session = AVAudioSession.sharedInstance()
    private var onFrame: ((Data) -> Void)?
    private let targetSampleRate: Double = 16_000

    public init() {}

    public func start(onFrame: @escaping (Data) -> Void) throws {
        self.onFrame = onFrame
        try session.setCategory(.playAndRecord, mode: .default, options: [.defaultToSpeaker, .allowBluetooth])
        try session.setPreferredSampleRate(targetSampleRate)
        try session.setActive(true)

        let input = engine.inputNode
        let format = input.inputFormat(forBus: 0)
        input.removeTap(onBus: 0)
        input.installTap(onBus: 0, bufferSize: 800, format: format) { [weak self] buffer, _ in
            self?.handle(buffer: buffer)
        }
        engine.prepare()
        try engine.start()
    }

    public func stop() {
        engine.inputNode.removeTap(onBus: 0)
        engine.stop()
        try? session.setActive(false)
        onFrame = nil
    }

    private func handle(buffer: AVAudioPCMBuffer) {
        guard let channelData = buffer.floatChannelData?.pointee else { return }
        let frameCount = Int(buffer.frameLength)
        var pcm16 = Data(capacity: frameCount * 2)
        for i in 0..<frameCount {
            let clamped = max(-1.0, min(1.0, channelData[i]))
            var sample = Int16(clamped * Float(Int16.max))
            pcm16.append(Data(bytes: &sample, count: MemoryLayout<Int16>.size))
        }
        onFrame?(pcm16)
    }
}
