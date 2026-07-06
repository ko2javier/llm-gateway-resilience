package com.example.llm_gateway_resilience.client;



import com.example.llm_gateway_resilience.model.ChatRequest;
import com.example.llm_gateway_resilience.model.ChatResponse;
import com.example.llm_gateway_resilience.model.TokenUsage;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/*
 * Implementación REAL de LlmClient: en vez de simular una respuesta como MockApiClient,
 * llama de verdad a la API de Groq (compatible con el formato de chat completions de OpenAI).
 * Al implementar la misma interfaz que el mock, el orchestrator puede intercambiar
 * ambos clientes sin cambiar su propio código (patrón Strategy).
 */
@Component
public class GroqApiClient implements LlmClient {

    // Cliente HTTP inyectado desde RestClientConfig (bean simple, sin timeouts configurados aún).
    private final RestClient restClient;

    // Se resuelven desde application.yml -> groq.api.*, que a su vez lee ${GROQ_API_KEY} del .env
    // gracias a spring-dotenv.
    @Value("${groq.api.key}")
    private String apiKey;

    @Value("${groq.api.base-url}")
    private String baseUrl;

    @Value("${groq.api.model}")
    private String model;

    public GroqApiClient(RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    // Reintenta y, si el fallo persiste, abre el circuito: ambas instancias apuntan a la
    // config "groqApi" de application.yml (resilience4j.retry / resilience4j.circuitbreaker).
    @Retry(name = "groqApi")
    @CircuitBreaker(name = "groqApi", fallbackMethod = "fallbackResponse")
    public ChatResponse generateResponse(ChatRequest request) {
        long startTime = System.currentTimeMillis();

        // Si el cliente no especifica maxTokens, se limita la respuesta a 256 tokens por defecto.
        Integer maxTokens = request.getMaxTokens() != null ? request.getMaxTokens() : 256;

        // Body en el formato que espera el endpoint /chat/completions de Groq (estilo OpenAI):
        // un solo mensaje de rol "user" con el prompt recibido.
        Map<String, Object> body = Map.of(
                "model", model,
                "messages", List.of(
                        Map.of("role", "user", "content", request.getPrompt())
                ),
                "max_tokens", maxTokens
        );

        // POST a Groq autenticado con Bearer token. La respuesta se deserializa como Map crudo
        // (sin DTO tipado) porque solo se necesitan un par de campos del JSON.
        Map<String, Object> responseBody = restClient.post()
                .uri(baseUrl + "/chat/completions")
                .header("Authorization", "Bearer " + apiKey)
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(Map.class);

        // Misma métrica de latencia que usa MockApiClient, para que ChatResponse.latencyMs
        // sea comparable entre el modo mock y el modo real.
        long latency = System.currentTimeMillis() - startTime;

        return parseGroqResponse(responseBody, latency);
    }

    // Método de fallback: misma firma + una excepción como último parámetro, requerido por
    // Resilience4j para invocarlo cuando el circuito está abierto o se agotan los reintentos.
    private ChatResponse fallbackResponse(ChatRequest request, Throwable t) {
        TokenUsage emptyUsage = new TokenUsage(0, 0, 0.0);
        return new ChatResponse(
                "El servicio de IA no está disponible en este momento (circuit breaker activo). Intenta de nuevo en unos segundos.",
                0,
                emptyUsage
        );
    }

    /*
     * Extrae del JSON crudo de Groq solo lo que ChatResponse necesita:
     * - choices[0].message.content -> el texto generado por el modelo.
     * - usage.prompt_tokens / completion_tokens -> tokens reales reportados por la API
     *   (a diferencia del mock, que solo estima con length() / 4).
     *
     * Nota: no valida forma inesperada del JSON (p.ej. una respuesta de error de Groq sin
     * "choices"), así que un fallo de la API real explota aquí con NPE/ClassCastException
     * en vez de un error controlado.
     */
    @SuppressWarnings("unchecked")
    private ChatResponse parseGroqResponse(Map<String, Object> responseBody, long latency) {
        if (responseBody == null) {
            throw new IllegalStateException("Groq no devolvió ninguna respuesta");
        }

        List<Map<String, Object>> choices = (List<Map<String, Object>>) responseBody.get("choices");
        Map<String, Object> firstChoice = choices.get(0);
        Map<String, Object> message = (Map<String, Object>) firstChoice.get("message");
        String content = (String) message.get("content");

        Map<String, Object> usageData = (Map<String, Object>) responseBody.get("usage");
        int promptTokens = (int) usageData.get("prompt_tokens");
        int completionTokens = (int) usageData.get("completion_tokens");

        // El coste se deja en 0.0: todavía no hay lógica de precio por token para Groq.
        TokenUsage usage = new TokenUsage(promptTokens, completionTokens, 0.0);

        return new ChatResponse(content, latency, usage);
    }
}