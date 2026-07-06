package com.example.llm_gateway_resilience.client;

import com.example.llm_gateway_resilience.model.ChatRequest;
import com.example.llm_gateway_resilience.model.ChatResponse;

public interface LlmClient {

    ChatResponse generateResponse(ChatRequest request);
}