package com.example.llm_gateway_resilience.controller;

import com.example.llm_gateway_resilience.model.ChatRequest;
import com.example.llm_gateway_resilience.service.LlmOrchestratorService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.*;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(LlmGatewayController.class)
class LlmGatewayControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private LlmOrchestratorService orchestratorService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void deberiaDevolver400CuandoPromptEstaVacio() throws Exception {
        ChatRequest requestVacio = new ChatRequest("", 100);

        mockMvc.perform(post("/api/v1/llm/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestVacio)))
                .andExpect(status().isBadRequest());

        verify(orchestratorService, never()).processRequest(any(), any());
    }

    @Test
    void deberiaDevolver200CuandoPromptEsValido() throws Exception {
        ChatRequest requestValido = new ChatRequest("Hola", 100);

        mockMvc.perform(post("/api/v1/llm/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestValido)))
                .andExpect(status().isOk());
    }
}