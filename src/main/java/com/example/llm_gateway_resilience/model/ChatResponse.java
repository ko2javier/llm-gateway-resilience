package com.example.llm_gateway_resilience.model;

public class ChatResponse {

    private String text;
    private long latencyMs;
    private TokenUsage usage;

    public ChatResponse() {
    }

    public ChatResponse(String text, long latencyMs, TokenUsage usage) {
        this.text = text;
        this.latencyMs = latencyMs;
        this.usage = usage;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public long getLatencyMs() {
        return latencyMs;
    }

    public void setLatencyMs(long latencyMs) {
        this.latencyMs = latencyMs;
    }

    public TokenUsage getUsage() {
        return usage;
    }

    public void setUsage(TokenUsage usage) {
        this.usage = usage;
    }
}