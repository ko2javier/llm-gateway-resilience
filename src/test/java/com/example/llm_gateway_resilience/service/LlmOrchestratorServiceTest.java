package com.example.llm_gateway_resilience.service;

import com.example.llm_gateway_resilience.client.GroqApiClient;
import com.example.llm_gateway_resilience.model.ChatRequest;
import com.example.llm_gateway_resilience.model.ChatResponse;
import com.example.llm_gateway_resilience.model.TokenUsage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LlmOrchestratorServiceTest {

    @Mock
    private GroqApiClient groqApiClient;

    private LlmOrchestratorService orchestratorService;

    @BeforeEach
    void setUp() {
        orchestratorService = new LlmOrchestratorService(groqApiClient);
    }

    @Test
    void deberiaDelegarEnGroqApiClient() {
        ChatRequest request = new ChatRequest("Hola", 100);
        ChatResponse respuestaEsperada = new ChatResponse("respuesta real", 300, new TokenUsage(5, 10, 0.02));

        when(groqApiClient.generateResponse(request)).thenReturn(respuestaEsperada);

        ChatResponse resultado = orchestratorService.processRequest(request);

        assertEquals(respuestaEsperada, resultado);
        verify(groqApiClient).generateResponse(request);
    }
}
