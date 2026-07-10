package com.example.llm_gateway_resilience.service;

import com.example.llm_gateway_resilience.client.GroqApiClient;
import com.example.llm_gateway_resilience.client.GroqCompletionResult;
import com.example.llm_gateway_resilience.client.GroqToolCall;
import com.example.llm_gateway_resilience.model.ChatRequest;
import com.example.llm_gateway_resilience.model.ChatResponse;
import com.example.llm_gateway_resilience.tool.WeatherResult;
import com.example.llm_gateway_resilience.tool.WeatherToolException;
import com.example.llm_gateway_resilience.tool.WeatherToolService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LlmOrchestratorServiceTest {

    @Mock
    private GroqApiClient groqApiClient;

    @Mock
    private WeatherToolService weatherToolService;

    private LlmOrchestratorService orchestratorService;

    @BeforeEach
    void setUp() {
        orchestratorService = new LlmOrchestratorService(groqApiClient, weatherToolService, new ObjectMapper());
    }

    @Test
    void deberiaDevolverTextoDirectoCuandoNoHayToolCall() {
        ChatRequest request = new ChatRequest("Hola", 100);
        GroqCompletionResult result = new GroqCompletionResult(
                "respuesta real", List.of(), Map.of("role", "assistant"), 5, 10);

        when(groqApiClient.callChatCompletion(any(), any(), eq(100))).thenReturn(result);

        ChatResponse response = orchestratorService.processRequest(request);

        assertEquals("respuesta real", response.getText());
        assertNull(response.getToolUsed());
        assertEquals(5, response.getUsage().getInputTokens());
        assertEquals(10, response.getUsage().getOutputTokens());
        verify(groqApiClient, times(1)).callChatCompletion(any(), any(), eq(100));
    }

    @Test
    void deberiaInvocarWeatherToolCuandoElModeloLoPide() {
        ChatRequest request = new ChatRequest("¿Qué tiempo hace en Madrid?", 100);

        GroqToolCall toolCall = new GroqToolCall("call_1", "get_current_weather", "{\"location\":\"Madrid\"}");
        GroqCompletionResult firstResult = new GroqCompletionResult(
                null, List.of(toolCall), Map.of("role", "assistant"), 8, 0);
        GroqCompletionResult secondResult = new GroqCompletionResult(
                "En Madrid hace sol", List.of(), Map.of("role", "assistant"), 20, 15);

        when(groqApiClient.callChatCompletion(any(), any(), eq(100)))
                .thenReturn(firstResult)
                .thenReturn(secondResult);
        when(weatherToolService.getCurrentWeather("Madrid"))
                .thenReturn(new WeatherResult("Madrid", 25.0, 10.0, "despejado"));

        ChatResponse response = orchestratorService.processRequest(request);

        assertEquals("En Madrid hace sol", response.getText());
        assertEquals("get_current_weather", response.getToolUsed());
        assertEquals(28, response.getUsage().getInputTokens());
        assertEquals(15, response.getUsage().getOutputTokens());
        verify(weatherToolService).getCurrentWeather("Madrid");
        verify(groqApiClient, times(2)).callChatCompletion(any(), any(), eq(100));
    }

    @Test
    void deberiaResponderConTextoDeDisculpaCuandoElToolDeTiempoFalla() {
        ChatRequest request = new ChatRequest("¿Qué tiempo hace en Marte?", 100);

        GroqToolCall toolCall = new GroqToolCall("call_1", "get_current_weather", "{\"location\":\"Marte\"}");
        GroqCompletionResult firstResult = new GroqCompletionResult(
                null, List.of(toolCall), Map.of("role", "assistant"), 8, 0);
        GroqCompletionResult secondResult = new GroqCompletionResult(
                "No he podido consultar el tiempo ahora mismo", List.of(), Map.of("role", "assistant"), 20, 12);

        when(groqApiClient.callChatCompletion(any(), any(), eq(100)))
                .thenReturn(firstResult)
                .thenReturn(secondResult);
        when(weatherToolService.getCurrentWeather("Marte"))
                .thenThrow(new WeatherToolException("Servicio de tiempo no disponible", new RuntimeException("timeout")));

        ChatResponse response = orchestratorService.processRequest(request);

        assertEquals("No he podido consultar el tiempo ahora mismo", response.getText());
        assertEquals("get_current_weather", response.getToolUsed());
    }
}
