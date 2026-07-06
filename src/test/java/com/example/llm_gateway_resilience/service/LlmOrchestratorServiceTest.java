package com.example.llm_gateway_resilience.service;

import com.example.llm_gateway_resilience.client.GroqApiClient;
import com.example.llm_gateway_resilience.client.MockApiClient;
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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LlmOrchestratorServiceTest {

    @Mock
    private MockApiClient mockApiClient;

    @Mock
    private GroqApiClient groqApiClient;

    private LlmOrchestratorService orchestratorService;

    @BeforeEach
    void setUp() {
        orchestratorService = new LlmOrchestratorService(mockApiClient, groqApiClient);
    }

    @Test
    void deberiaLlamarAlMockCuandoNoHayHeaderRealMode() {
        // ARRANGE: preparamos los datos y el comportamiento simulado
        ChatRequest request = new ChatRequest("Hola", 100);
        ChatResponse respuestaEsperada = new ChatResponse("respuesta mock", 1500, new TokenUsage(5, 10, 0.0));

        when(mockApiClient.generateResponse(request)).thenReturn(respuestaEsperada);

        // ACT: ejecutamos el método real que queremos probar
        ChatResponse resultado = orchestratorService.processRequest(request, null);

        // ASSERT: verificamos que el resultado es el esperado
        assertEquals(respuestaEsperada, resultado);
        verify(mockApiClient).generateResponse(request);
        verifyNoInteractions(groqApiClient);
    }

    @Test
    void deberiaLlamarAGroqCuandoHeaderEsReal() {
        ChatRequest request = new ChatRequest("Hola", 100);
        ChatResponse respuestaEsperada = new ChatResponse("respuesta real", 300, new TokenUsage(5, 10, 0.02));

        when(groqApiClient.generateResponse(request)).thenReturn(respuestaEsperada);

        ChatResponse resultado = orchestratorService.processRequest(request, "REAL");

        assertEquals(respuestaEsperada, resultado);
        verify(groqApiClient).generateResponse(request);
        verifyNoInteractions(mockApiClient);
    }

    @Test
    void deberiaLlamarAlMockCuandoHeaderTieneValorDistintoDeReal() {
        ChatRequest request = new ChatRequest("Hola", 100);
        ChatResponse respuestaEsperada = new ChatResponse("respuesta mock", 1500, new TokenUsage(5, 10, 0.0));

        when(mockApiClient.generateResponse(request)).thenReturn(respuestaEsperada);

        ChatResponse resultado = orchestratorService.processRequest(request, "cualquier-otra-cosa");

        assertEquals(respuestaEsperada, resultado);
        verify(mockApiClient).generateResponse(request);
        verifyNoInteractions(groqApiClient);
    }
}