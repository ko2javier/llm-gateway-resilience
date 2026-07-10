package com.example.llm_gateway_resilience.client;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class GroqApiClient {

    private final RestClient restClient;

    @Value("${groq.api.key}")
    private String apiKey;

    @Value("${groq.api.base-url}")
    private String baseUrl;

    @Value("${groq.api.model}")
    private String model;

    public GroqApiClient(RestClient restClient) {
        this.restClient = restClient;
    }

    @Retry(name = "groqApi")
    @CircuitBreaker(name = "groqApi", fallbackMethod = "fallbackCompletion")
    public GroqCompletionResult callChatCompletion(List<Map<String, Object>> messages, List<Map<String, Object>> tools, int maxTokens) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", messages);
        body.put("max_tokens", maxTokens);
        if (tools != null && !tools.isEmpty()) {
            body.put("tools", tools);
        }

        Map<String, Object> responseBody = restClient.post()
                .uri(baseUrl + "/chat/completions")
                .header("Authorization", "Bearer " + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(Map.class);

        return parseGroqResponse(responseBody);
    }

    private GroqCompletionResult fallbackCompletion(List<Map<String, Object>> messages, List<Map<String, Object>> tools, int maxTokens, Throwable t) {
        return new GroqCompletionResult(
                "El servicio de IA no está disponible en este momento. Intenta de nuevo en unos segundos.",
                List.of(),
                null,
                0,
                0
        );
    }

    @SuppressWarnings("unchecked")
    private GroqCompletionResult parseGroqResponse(Map<String, Object> responseBody) {
        if (responseBody == null) {
            throw new IllegalStateException("Respuesta vacía de Groq");
        }

        List<Map<String, Object>> choices = (List<Map<String, Object>>) responseBody.get("choices");
        if (choices == null || choices.isEmpty()) {
            Object error = responseBody.get("error");
            throw new IllegalStateException("Groq no devolvió choices" + (error != null ? ": " + error : ""));
        }

        Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
        if (message == null) {
            throw new IllegalStateException("Estructura de respuesta inesperada de Groq");
        }

        Map<String, Object> usageData = (Map<String, Object>) responseBody.get("usage");
        int promptTokens = usageData != null ? (int) usageData.get("prompt_tokens") : 0;
        int completionTokens = usageData != null ? (int) usageData.get("completion_tokens") : 0;

        List<GroqToolCall> toolCalls = parseToolCalls(message);

        return new GroqCompletionResult(
                (String) message.get("content"),
                toolCalls,
                message,
                promptTokens,
                completionTokens
        );
    }

    @SuppressWarnings("unchecked")
    private List<GroqToolCall> parseToolCalls(Map<String, Object> message) {
        List<Map<String, Object>> rawToolCalls = (List<Map<String, Object>>) message.get("tool_calls");
        if (rawToolCalls == null) {
            return List.of();
        }

        List<GroqToolCall> toolCalls = new ArrayList<>();
        for (Map<String, Object> raw : rawToolCalls) {
            Map<String, Object> function = (Map<String, Object>) raw.get("function");
            toolCalls.add(new GroqToolCall(
                    (String) raw.get("id"),
                    (String) function.get("name"),
                    (String) function.get("arguments")
            ));
        }
        return toolCalls;
    }
}
