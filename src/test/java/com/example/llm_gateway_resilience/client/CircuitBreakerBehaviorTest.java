package com.example.llm_gateway_resilience.client;

import com.example.llm_gateway_resilience.model.ChatRequest;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpServerErrorException;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CircuitBreakerBehaviorTest {

    @Test
    void deberiaAbrirseTrasSuperarUmbralDeFallos() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .slidingWindowSize(10)
                .minimumNumberOfCalls(5)
                .failureRateThreshold(50)
                .build();

        CircuitBreaker circuitBreaker = CircuitBreaker.of("groqApiTest", config);

        for (int i = 0; i < 6; i++) {
            try {
                circuitBreaker.executeCallable(() -> {
                    throw HttpServerErrorException.create(
                            HttpStatus.SERVICE_UNAVAILABLE, "fail", null, null, null);
                });
            } catch (Exception ignored) {
                // esperado: simulamos que Groq falla repetidamente
            }
        }

        assertEquals(CircuitBreaker.State.OPEN, circuitBreaker.getState());
    }
}