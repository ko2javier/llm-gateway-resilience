package com.example.llm_gateway_resilience.client;

import com.example.llm_gateway_resilience.model.ChatRequest;
import com.example.llm_gateway_resilience.model.ChatResponse;
import com.example.llm_gateway_resilience.model.TokenUsage;
import org.springframework.stereotype.Component;

@Component
public class MockApiClient implements LlmClient {

    @Override
    public ChatResponse generateResponse(ChatRequest request) {
        long startTime = System.currentTimeMillis();

        try {
            Thread.sleep(1500); // simula latencia real de un LLM
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        long latency = System.currentTimeMillis() - startTime;

        String simulatedText = "Esta es una respuesta simulada para el prompt: \"" + request.getPrompt() + "\"";
        /*
        * Cálculo de tokens aproximado (length() / 4): es una estimación tosca típica
        * (4 caracteres ≈ 1 token en inglés), solo para que el mock no devuelva ceros absolutos.
        *  No es precisa ni falta que lo sea, es un mock.
        * */
        TokenUsage usage = new TokenUsage(
                request.getPrompt().length() / 4,   // input tokens aproximados
                simulatedText.length() / 4,          // output tokens aproximados
                0.0                                   // coste 0, porque es mock
        );

        return new ChatResponse(simulatedText, latency, usage);
    }
}