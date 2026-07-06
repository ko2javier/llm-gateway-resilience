package com.example.llm_gateway_resilience.service;

import com.example.llm_gateway_resilience.client.GroqApiClient;
import com.example.llm_gateway_resilience.client.LlmClient;
import com.example.llm_gateway_resilience.client.MockApiClient;
import com.example.llm_gateway_resilience.model.ChatRequest;
import com.example.llm_gateway_resilience.model.ChatResponse;
import org.springframework.stereotype.Service;

@Service
public class LlmOrchestratorService {

    private static final String REAL_MODE_HEADER_VALUE = "REAL";

    private final LlmClient mockApiClient;
    private final LlmClient groqApiClient;

    public LlmOrchestratorService(MockApiClient mockApiClient, GroqApiClient groqApiClient) {
        this.mockApiClient = mockApiClient;
        this.groqApiClient = groqApiClient;
    }

    public ChatResponse processRequest(ChatRequest request, String executionModeHeader) {

        boolean isRealModeRequested = REAL_MODE_HEADER_VALUE.equalsIgnoreCase(executionModeHeader);

        if (isRealModeRequested) {
            return groqApiClient.generateResponse(request);
        }

        return mockApiClient.generateResponse(request);
    }
}