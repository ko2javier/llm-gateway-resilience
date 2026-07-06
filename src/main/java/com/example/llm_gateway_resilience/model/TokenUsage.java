package com.example.llm_gateway_resilience.model;

public class TokenUsage {

    private int inputTokens;
    private int outputTokens;
    private double estimatedCost;

    public TokenUsage() {
    }

    public TokenUsage(int inputTokens, int outputTokens, double estimatedCost) {
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
        this.estimatedCost = estimatedCost;
    }

    public int getInputTokens() {
        return inputTokens;
    }

    public void setInputTokens(int inputTokens) {
        this.inputTokens = inputTokens;
    }

    public int getOutputTokens() {
        return outputTokens;
    }

    public void setOutputTokens(int outputTokens) {
        this.outputTokens = outputTokens;
    }

    public double getEstimatedCost() {
        return estimatedCost;
    }

    public void setEstimatedCost(double estimatedCost) {
        this.estimatedCost = estimatedCost;
    }

    public int getTotalTokens() {
        return inputTokens + outputTokens;
    }
}