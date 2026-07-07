# LLM Gateway — Resilience4j + Spring Boot 3

API gateway for LLM providers built with Spring Boot 3 and Java 21. Wraps the Groq API with production-grade resilience patterns and a full observability stack.

## What it does

Exposes a single chat endpoint that forwards requests to Groq's LLM API, protected by automatic retry and circuit breaker logic. If Groq is down or rate-limiting, the gateway degrades gracefully instead of cascading failures to the client.

## Tech stack

| Layer | Technology |
|---|---|
| Runtime | Java 21, Spring Boot 3.5 |
| Resilience | Resilience4j (circuit breaker + retry) |
| HTTP client | Spring `RestClient` |
| Observability | Micrometer → Prometheus → Grafana |
| API docs | Springdoc / Swagger UI |
| LLM provider | Groq (`llama-3.3-70b-versatile`) |

## Architecture

```
Client
  └── POST /api/v1/llm/chat
        └── LlmGatewayController
              └── LlmOrchestratorService
                    └── GroqApiClient
                          ├── @Retry       — up to 3 attempts on 5xx / timeout
                          └── @CircuitBreaker — opens after 50% failure rate (min 5 calls)
                                └── fallback — returns graceful error message
```

**Resilience behavior:**
- 5xx or timeout → retries up to 3 times with 500ms wait
- Circuit opens after ≥5 calls with ≥50% failure rate
- While open, requests skip Groq entirely and get an immediate fallback response
- Circuit moves to half-open after 10s, allows 3 test calls to decide if it closes

**Error handling:**
- `401` from Groq → `401` with message about invalid API key
- `429` from Groq → `429` with rate limit message
- `5xx` from Groq → `502 Bad Gateway`
- Circuit open → `200` with graceful degradation message

## Running locally

**Prerequisites:** Java 21, a free [Groq API key](https://console.groq.com), Docker (for observability stack)

```bash
# 1. Clone and create .env
cp .env.example .env
# Edit .env and add your GROQ_API_KEY

# 2. Start the app
./gradlew bootRun

# 3. (Optional) Start Prometheus + Grafana
docker compose up -d
```

| Service | URL |
|---|---|
| API | http://localhost:8085 |
| Swagger UI | http://localhost:8085/swagger-ui/index.html |
| Actuator / health | http://localhost:8085/actuator/health |
| Prometheus | http://localhost:9091 |
| Grafana | http://localhost:3001 |

## API

### POST /api/v1/llm/chat

```bash
curl -X POST http://localhost:8085/api/v1/llm/chat \
  -H "Content-Type: application/json" \
  -d '{"prompt": "Explain what a circuit breaker is in two sentences", "maxTokens": 200}'
```

**Request**
```json
{
  "prompt": "Your question here",
  "maxTokens": 200
}
```

**Response**
```json
{
  "text": "A circuit breaker is...",
  "latencyMs": 843,
  "tokenUsage": {
    "promptTokens": 18,
    "completionTokens": 47,
    "cost": 0.0
  }
}
```

## Observability

JVM metrics, HTTP request rates, error rates, and circuit breaker state are exposed via `/actuator/prometheus` and visualized in Grafana (dashboard [11378](https://grafana.com/grafana/dashboards/11378)).

## Configuration

| Property | Default | Description |
|---|---|---|
| `SERVER_PORT` | `8085` | App port |
| `GROQ_API_KEY` | — | Required. Set in `.env` |
| Retry attempts | 3 | `resilience4j.retry.instances.groqApi.max-attempts` |
| CB failure threshold | 50% | `resilience4j.circuitbreaker.instances.groqApi.failure-rate-threshold` |
| CB window size | 10 calls | `resilience4j.circuitbreaker.instances.groqApi.sliding-window-size` |
