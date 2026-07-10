package com.example.llm_gateway_resilience.tool;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Component
public class WeatherToolService {

    private static final String GEOCODING_URL = "https://geocoding-api.open-meteo.com/v1/search";
    private static final String FORECAST_URL = "https://api.open-meteo.com/v1/forecast";

    private final RestClient restClient;

    public WeatherToolService(RestClient restClient) {
        this.restClient = restClient;
    }

    @SuppressWarnings("unchecked")
    @Retry(name = "weatherApi")
    @CircuitBreaker(name = "weatherApi", fallbackMethod = "fallbackWeather")
    public WeatherResult getCurrentWeather(String location) {
        Map<String, Object> geocoding = restClient.get()
                .uri(GEOCODING_URL + "?name={name}&count=1&language=es&format=json", location)
                .retrieve()
                .body(Map.class);

        Map<String, Object> place = extractFirstGeocodingResult(geocoding, location);
        double latitude = ((Number) place.get("latitude")).doubleValue();
        double longitude = ((Number) place.get("longitude")).doubleValue();
        String resolvedName = (String) place.get("name");

        Map<String, Object> forecast = restClient.get()
                .uri(FORECAST_URL + "?latitude={lat}&longitude={lon}&current=temperature_2m,weather_code,wind_speed_10m&timezone=auto",
                        latitude, longitude)
                .retrieve()
                .body(Map.class);

        Map<String, Object> current = forecast != null ? (Map<String, Object>) forecast.get("current") : null;
        if (current == null) {
            throw new IllegalStateException("Open-Meteo no devolvió datos 'current' para " + location);
        }

        double temperature = ((Number) current.get("temperature_2m")).doubleValue();
        double windSpeed = ((Number) current.get("wind_speed_10m")).doubleValue();
        int weatherCode = ((Number) current.get("weather_code")).intValue();

        return new WeatherResult(resolvedName, temperature, windSpeed, describeWeatherCode(weatherCode));
    }

    private WeatherResult fallbackWeather(String location, Throwable t) {
        throw new WeatherToolException("Servicio de tiempo no disponible para " + location, t);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractFirstGeocodingResult(Map<String, Object> geocoding, String location) {
        if (geocoding == null) {
            throw new IllegalStateException("Respuesta vacía del servicio de geocoding");
        }
        List<Map<String, Object>> results = (List<Map<String, Object>>) geocoding.get("results");
        if (results == null || results.isEmpty()) {
            throw new IllegalStateException("No se encontró la ubicación: " + location);
        }
        return results.get(0);
    }

    private String describeWeatherCode(int code) {
        if (code == 0) return "despejado";
        if (code <= 3) return "parcialmente nuboso";
        if (code == 45 || code == 48) return "niebla";
        if (code >= 51 && code <= 67) return "lluvia";
        if (code >= 71 && code <= 77) return "nieve";
        if (code >= 80 && code <= 82) return "chubascos";
        if (code >= 95) return "tormenta";
        return "condiciones variables";
    }

    public static Map<String, Object> toolDefinition() {
        return Map.of(
                "type", "function",
                "function", Map.of(
                        "name", "get_current_weather",
                        "description", "Obtiene el tiempo actual (temperatura, viento y condición) para una ciudad o ubicación dada.",
                        "parameters", Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "location", Map.of(
                                                "type", "string",
                                                "description", "Nombre de la ciudad, por ejemplo 'Madrid' o 'Buenos Aires'"
                                        )
                                ),
                                "required", List.of("location")
                        )
                )
        );
    }
}
