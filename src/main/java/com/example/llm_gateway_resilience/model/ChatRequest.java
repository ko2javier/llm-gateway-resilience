package com.example.llm_gateway_resilience.model;

public class ChatRequest {

    private String prompt;
    private Integer maxTokens;

    public ChatRequest() {
    }

    public ChatRequest(String prompt, Integer maxTokens) {
        this.prompt = prompt;
        this.maxTokens = maxTokens;
    }

    public String getPrompt() {
        return prompt;
    }

    public void setPrompt(String prompt) {
        this.prompt = prompt;
    }

    public Integer getMaxTokens() {
        return maxTokens;
    }

    public void setMaxTokens(Integer maxTokens) {
        this.maxTokens = maxTokens;
    }
}