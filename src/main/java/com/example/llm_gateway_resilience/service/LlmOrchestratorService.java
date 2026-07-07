package com.example.llm_gateway_resilience.service;

import com.example.llm_gateway_resilience.client.GroqApiClient;
import com.example.llm_gateway_resilience.model.ChatRequest;
import com.example.llm_gateway_resilience.model.ChatResponse;
import org.springframework.stereotype.Service;

@Service
public class LlmOrchestratorService {

    private final GroqApiClient groqApiClient;

    public LlmOrchestratorService(GroqApiClient groqApiClient) {
        this.groqApiClient = groqApiClient;
    }

    public ChatResponse processRequest(ChatRequest request) {
        return groqApiClient.generateResponse(request);
    }
}
