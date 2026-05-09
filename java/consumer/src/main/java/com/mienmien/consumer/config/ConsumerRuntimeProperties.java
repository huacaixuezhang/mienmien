package com.mienmien.consumer.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "mienmien.consumer")
public class ConsumerRuntimeProperties {
    /**
     * 为 true 时，HTTP 探测 {@code /api/v1/consumer/health/stream} 返回 503（CON-5031），便于网关与客户端预检。
     */
    private boolean streamDegraded;
    private int sampleRate = 16000;
    private int frameSize = 800;
    private String defaultMode = "unsupervised";
    private double similarityThreshold = 0.7;

    public boolean isStreamDegraded() {
        return streamDegraded;
    }

    public void setStreamDegraded(boolean streamDegraded) {
        this.streamDegraded = streamDegraded;
    }

    public int getSampleRate() {
        return sampleRate;
    }

    public void setSampleRate(int sampleRate) {
        this.sampleRate = sampleRate;
    }

    public int getFrameSize() {
        return frameSize;
    }

    public void setFrameSize(int frameSize) {
        this.frameSize = frameSize;
    }

    public String getDefaultMode() {
        return defaultMode;
    }

    public void setDefaultMode(String defaultMode) {
        this.defaultMode = defaultMode;
    }

    public double getSimilarityThreshold() {
        return similarityThreshold;
    }

    public void setSimilarityThreshold(double similarityThreshold) {
        this.similarityThreshold = similarityThreshold;
    }
}
