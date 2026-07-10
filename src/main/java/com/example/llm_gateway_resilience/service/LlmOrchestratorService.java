package com.example.llm_gateway_resilience.service;

import com.example.llm_gateway_resilience.client.GroqApiClient;
import com.example.llm_gateway_resilience.client.GroqCompletionResult;
import com.example.llm_gateway_resilience.client.GroqToolCall;
import com.example.llm_gateway_resilience.model.ChatRequest;
import com.example.llm_gateway_resilience.model.ChatResponse;
import com.example.llm_gateway_resilience.model.TokenUsage;
import com.example.llm_gateway_resilience.tool.WeatherResult;
import com.example.llm_gateway_resilience.tool.WeatherToolException;
import com.example.llm_gateway_resilience.tool.WeatherToolService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class LlmOrchestratorService {

    private static final String WEATHER_TOOL_NAME = "get_current_weather";
    private static final int DEFAULT_MAX_TOKENS = 256;

    private final GroqApiClient groqApiClient;
    private final WeatherToolService weatherToolService;
    private final ObjectMapper objectMapper;

    public LlmOrchestratorService(GroqApiClient groqApiClient, WeatherToolService weatherToolService, ObjectMapper objectMapper) {
        this.groqApiClient = groqApiClient;
        this.weatherToolService = weatherToolService;
        this.objectMapper = objectMapper;
    }

    public ChatResponse processRequest(ChatRequest request) {
        int maxTokens = request.getMaxTokens() != null ? request.getMaxTokens() : DEFAULT_MAX_TOKENS;
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "user", "content", request.getPrompt()));

        long startTime = System.currentTimeMillis();
        GroqCompletionResult firstResult = groqApiClient.callChatCompletion(
                messages, List.of(WeatherToolService.toolDefinition()), maxTokens);

        if (firstResult.toolCalls().isEmpty()) {
            return buildResponse(firstResult, null, System.currentTimeMillis() - startTime);
        }

        GroqToolCall toolCall = firstResult.toolCalls().get(0);
        messages.add(firstResult.rawAssistantMessage());
        messages.add(Map.of(
                "role", "tool",
                "tool_call_id", toolCall.id(),
                "content", executeToolCall(toolCall)
        ));

        GroqCompletionResult secondResult = groqApiClient.callChatCompletion(messages, null, maxTokens);
        long latencyMs = System.currentTimeMillis() - startTime;

        return buildResponse(mergeUsage(firstResult, secondResult), toolCall.name(), latencyMs);
    }

    private String executeToolCall(GroqToolCall toolCall) {
        if (!WEATHER_TOOL_NAME.equals(toolCall.name())) {
            return "{\"error\": \"Tool desconocido: " + toolCall.name() + "\"}";
        }

        String location;
        try {
            location = objectMapper.readTree(toolCall.argumentsJson()).get("location").asText();
        } catch (Exception e) {
            return "{\"error\": \"No se pudo interpretar la ubicación solicitada\"}";
        }

        try {
            WeatherResult weather = weatherToolService.getCurrentWeather(location);
            return objectMapper.writeValueAsString(weather);
        } catch (WeatherToolException e) {
            return "{\"error\": \"No se pudo obtener el tiempo en este momento\"}";
        } catch (Exception e) {
            return "{\"error\": \"Error inesperado al consultar el tiempo\"}";
        }
    }

    private GroqCompletionResult mergeUsage(GroqCompletionResult first, GroqCompletionResult second) {
        return new GroqCompletionResult(
                second.content(),
                second.toolCalls(),
                second.rawAssistantMessage(),
                first.promptTokens() + second.promptTokens(),
                first.completionTokens() + second.completionTokens()
        );
    }

    private ChatResponse buildResponse(GroqCompletionResult result, String toolUsed, long latencyMs) {
        TokenUsage usage = new TokenUsage(result.promptTokens(), result.completionTokens(), 0.0);
        return new ChatResponse(result.content(), latencyMs, usage, toolUsed);
    }
}
