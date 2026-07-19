# LLM Gateway — Resilience4j + Spring Boot 3

API gateway for LLM providers built with Spring Boot 3 and Java 21. Wraps the Groq API with production-grade resilience patterns and a full observability stack.

**Live demo:** [llm.ko2-oreilly.com](https://llm.ko2-oreilly.com) · **Swagger:** [llm.ko2-oreilly.com/swagger-ui](https://llm.ko2-oreilly.com/swagger-ui/index.html)

## What it does

Exposes a single chat endpoint that forwards requests to Groq's LLM API, protected by automatic retry and circuit breaker logic. If Groq is down or rate-limiting, the gateway degrades gracefully instead of cascading failures to the client.

The model can also invoke a `get_current_weather` function tool (backed by the free [Open-Meteo](https://open-meteo.com) API) — if the prompt needs live weather data, Groq requests the tool call, the gateway resolves it, and feeds the result back for a final answer. The weather tool has its own independent retry/circuit-breaker instance, so a flaky weather API degrades to an apologetic answer instead of a 500.

## Tech stack

| Layer | Technology |
|---|---|
| Runtime | Java 21, Spring Boot 3.5 |
| Resilience | Resilience4j (circuit breaker + retry) |
| HTTP client | Spring `RestClient` |
| Observability | Micrometer → Prometheus → Grafana |
| API docs | Springdoc / Swagger UI |
| LLM provider | Groq (`llama-3.3-70b-versatile`) |
| Weather tool | Open-Meteo (free, no API key) |
| Deploy | Docker · Hetzner VPS · GitHub Actions CI/CD |
| SSL / Proxy | Cloudflare (Full) + Nginx |

## Architecture

```
Client
  └── POST /api/v1/llm/chat
        └── LlmGatewayController
              └── LlmOrchestratorService
                    ├── GroqApiClient.callChatCompletion()      — 1st call, with tool definitions
                    │     ├── @Retry       — up to 3 attempts on 5xx / timeout
                    │     └── @CircuitBreaker (groqApi) — opens after 50% failure rate (min 5 calls)
                    │           └── fallback — returns graceful error message
                    ├── WeatherToolService.getCurrentWeather()  — only if Groq requested the tool
                    │     ├── @Retry       — up to 3 attempts on 5xx / timeout
                    │     └── @CircuitBreaker (weatherApi) — independent from groqApi
                    │           └── fallback — tool error fed back to the model, not a 500
                    └── GroqApiClient.callChatCompletion()      — 2nd call, with the tool result
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
  "usage": {
    "inputTokens": 18,
    "outputTokens": 47,
    "estimatedCost": 0.0,
    "totalTokens": 65
  },
  "toolUsed": null
}
```

### Function tool calling (weather)

If the prompt needs live weather data, the model calls `get_current_weather` internally — no change to the request shape, just look at `toolUsed` in the response:

```bash
curl -X POST http://localhost:8085/api/v1/llm/chat \
  -H "Content-Type: application/json" \
  -d '{"prompt": "What is the weather like in Madrid right now?", "maxTokens": 200}'
```

```json
{
  "text": "It's currently 25°C and clear skies in Madrid...",
  "latencyMs": 1204,
  "usage": { "inputTokens": 143, "outputTokens": 32, "estimatedCost": 0.0, "totalTokens": 175 },
  "toolUsed": "get_current_weather"
}
```

### GET /api/v1/status

Lightweight resilience snapshot for building live dashboards/frontends — flattens the state of both circuit breaker instances (`groqApi`, `weatherApi`) plus retry counters into a single JSON response, no Actuator parsing required.

```bash
curl http://localhost:8085/api/v1/status
```

```json
{
  "groqApi":    { "state": "CLOSED", "failedCalls": 0, "bufferedCalls": 3, "retryAttempts": 0 },
  "weatherApi": { "state": "CLOSED", "failedCalls": 0, "bufferedCalls": 1, "retryAttempts": 0 }
}
```

`state` is one of `CLOSED`, `OPEN`, `HALF_OPEN`. Open CORS is enabled on `/api/v1/**` (any origin, `GET`/`POST`) so this — and `/api/v1/llm/chat` — can be called directly from a browser-based frontend without a proxy.

## Observability

JVM metrics, HTTP request rates, error rates, circuit breaker state, and retry outcomes are exposed via `/actuator/prometheus`.

**In production**, this app doesn't run its own Prometheus/Grafana — it's scraped by the shared observability stack of a sibling ecosystem (KO2 Platform, same Hetzner VPS), with a dedicated dashboard:

**Live dashboard:** [api.ko2-oreilly.com/grafana](https://api.ko2-oreilly.com/grafana/) → *LLM Gateway Resilience* (login: `user` / `user123`)

Panels: circuit breaker open/closed state (per instance: `groqApi`, `weatherApi`), retry outcomes, HTTP request rate, average latency, JVM heap/threads.

**Locally**, the bundled `docker-compose.yml` (Prometheus on `:9091`, Grafana on `:3001`) is available for standalone demoing without depending on the shared stack — see [Running locally](#running-locally).

## Configuration

| Property | Default | Description |
|---|---|---|
| `SERVER_PORT` | `8085` | App port |
| `GROQ_API_KEY` | — | Required. Set in `.env` |
| Retry attempts | 3 | `resilience4j.retry.instances.groqApi.max-attempts` |
| CB failure threshold | 50% | `resilience4j.circuitbreaker.instances.groqApi.failure-rate-threshold` |
| CB window size | 10 calls | `resilience4j.circuitbreaker.instances.groqApi.sliding-window-size` |
| Weather tool retry/CB | same defaults, separate instance | `resilience4j.*.instances.weatherApi.*` |
| CORS | open (`*`) on `/api/v1/**` | `WebConfig` — portfolio project, not locked down on purpose |
