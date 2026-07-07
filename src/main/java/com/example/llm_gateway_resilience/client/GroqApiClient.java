package com.example.llm_gateway_resilience.client;

import com.example.llm_gateway_resilience.model.ChatRequest;
import com.example.llm_gateway_resilience.model.ChatResponse;
import com.example.llm_gateway_resilience.model.TokenUsage;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

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
    @CircuitBreaker(name = "groqApi", fallbackMethod = "fallbackResponse")
    public ChatResponse generateResponse(ChatRequest request) {
        long startTime = System.currentTimeMillis();
        Integer maxTokens = request.getMaxTokens() != null ? request.getMaxTokens() : 256;

        Map<String, Object> body = Map.of(
                "model", model,
                "messages", List.of(Map.of("role", "user", "content", request.getPrompt())),
                "max_tokens", maxTokens
        );

        Map<String, Object> responseBody = restClient.post()
                .uri(baseUrl + "/chat/completions")
                .header("Authorization", "Bearer " + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(Map.class);

        return parseGroqResponse(responseBody, System.currentTimeMillis() - startTime);
    }

    private ChatResponse fallbackResponse(ChatRequest request, Throwable t) {
        return new ChatResponse(
                "El servicio de IA no está disponible en este momento. Intenta de nuevo en unos segundos.",
                0,
                new TokenUsage(0, 0, 0.0)
        );
    }

    @SuppressWarnings("unchecked")
    private ChatResponse parseGroqResponse(Map<String, Object> responseBody, long latency) {
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

        return new ChatResponse(
                (String) message.get("content"),
                latency,
                new TokenUsage(promptTokens, completionTokens, 0.0)
        );
    }
}
