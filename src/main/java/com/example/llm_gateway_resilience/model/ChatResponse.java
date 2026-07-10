package com.example.llm_gateway_resilience.model;

public class ChatResponse {

    private String text;
    private long latencyMs;
    private TokenUsage usage;
    private String toolUsed;

    public ChatResponse() {
    }

    public ChatResponse(String text, long latencyMs, TokenUsage usage) {
        this(text, latencyMs, usage, null);
    }

    public ChatResponse(String text, long latencyMs, TokenUsage usage, String toolUsed) {
        this.text = text;
        this.latencyMs = latencyMs;
        this.usage = usage;
        this.toolUsed = toolUsed;
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

    public String getToolUsed() {
        return toolUsed;
    }

    public void setToolUsed(String toolUsed) {
        this.toolUsed = toolUsed;
    }
}