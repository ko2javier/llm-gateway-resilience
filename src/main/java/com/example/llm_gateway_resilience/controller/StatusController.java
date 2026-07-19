package com.example.llm_gateway_resilience.controller;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class StatusController {

    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final RetryRegistry retryRegistry;

    public StatusController(CircuitBreakerRegistry circuitBreakerRegistry, RetryRegistry retryRegistry) {
        this.circuitBreakerRegistry = circuitBreakerRegistry;
        this.retryRegistry = retryRegistry;
    }

    @GetMapping("/status")
    public Map<String, Object> status() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("groqApi", instanceStatus("groqApi"));
        response.put("weatherApi", instanceStatus("weatherApi"));
        return response;
    }

    private Map<String, Object> instanceStatus(String name) {
        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker(name);
        Retry.Metrics retryMetrics = retryRegistry.retry(name).getMetrics();

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("state", cb.getState().name());
        details.put("failedCalls", cb.getMetrics().getNumberOfFailedCalls());
        details.put("bufferedCalls", cb.getMetrics().getNumberOfBufferedCalls());
        details.put("retryAttempts",
                retryMetrics.getNumberOfSuccessfulCallsWithRetryAttempt()
                        + retryMetrics.getNumberOfFailedCallsWithRetryAttempt());
        return details;
    }
}
