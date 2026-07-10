package com.example.llm_gateway_resilience.client;

import java.util.List;
import java.util.Map;

public record GroqCompletionResult(
        String content,
        List<GroqToolCall> toolCalls,
        Map<String, Object> rawAssistantMessage,
        int promptTokens,
        int completionTokens
) {
}
