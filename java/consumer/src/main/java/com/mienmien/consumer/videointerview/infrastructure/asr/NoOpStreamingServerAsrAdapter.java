package com.mienmien.consumer.videointerview.infrastructure.asr;

import com.mienmien.consumer.videointerview.application.port.StreamingServerAsrAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class NoOpStreamingServerAsrAdapter implements StreamingServerAsrAdapter {
    private static final Logger log = LoggerFactory.getLogger(NoOpStreamingServerAsrAdapter.class);

    @Override
    public void resetSession(String videoSessionId) {
        log.trace("asr.reset session={}", videoSessionId);
    }

    @Override
    public Optional<String> offerPcm16Le(String videoSessionId, int sampleRate, byte[] pcmFrame) {
        return Optional.empty();
    }
}
