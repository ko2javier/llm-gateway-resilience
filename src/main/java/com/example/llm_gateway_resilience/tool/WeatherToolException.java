package com.example.llm_gateway_resilience.tool;

public class WeatherToolException extends RuntimeException {

    public WeatherToolException(String message, Throwable cause) {
        super(message, cause);
    }
}
