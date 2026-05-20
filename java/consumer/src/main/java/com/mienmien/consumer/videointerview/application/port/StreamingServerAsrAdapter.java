package com.mienmien.consumer.videointerview.application.port;

import java.util.Optional;

/**
 * 服务端 ASR 适配器占位：MVP 由浏览器 Web Speech 转写后经 WebSocket 上送文本；
 * 后续可在此对接阿里云 Paraformer 实时识别等。
 */
public interface StreamingServerAsrAdapter {

    void resetSession(String videoSessionId);

    /** 上行 PCM16LE 单声道帧；无服务端识别时返回 empty。 */
    Optional<String> offerPcm16Le(String videoSessionId, int sampleRate, byte[] pcmFrame);
}
