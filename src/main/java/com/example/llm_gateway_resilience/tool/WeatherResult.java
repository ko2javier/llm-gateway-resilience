package com.example.llm_gateway_resilience.tool;

public record WeatherResult(String location, double temperatureC, double windSpeedKmh, String condition) {
}
