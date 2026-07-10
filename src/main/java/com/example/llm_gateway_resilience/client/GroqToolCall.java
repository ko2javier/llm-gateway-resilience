package com.example.llm_gateway_resilience.client;

public record GroqToolCall(String id, String name, String argumentsJson) {
}
