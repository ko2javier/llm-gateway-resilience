# llm-gateway-resilience

Gateway en Spring Boot que expone un endpoint de chat y llama a la API real de Groq, con capa de resiliencia (Resilience4j) envolviendo el cliente.

## Build

- Gradle wrapper: **8.14.5**
- Plugins: `org.springframework.boot:3.5.16` + `io.spring.dependency-management:1.1.7`
- Java toolchain: **21**
- Paquete raíz: `com.example.llm_gateway_resilience`

Dependencias añadidas sobre el scaffold de Initializr:
- `spring-boot-starter-web`
- `spring-boot-starter-actuator` + `micrometer-registry-prometheus`
- `resilience4j-spring-boot3:2.2.0` + `spring-boot-starter-aop`
- `springdoc-openapi-starter-webmvc-ui:2.5.0`
- `me.paulschwarz:spring-dotenv:4.0.0` — carga `.env` al arrancar para que `${GROQ_API_KEY}` en `application.yml` se resuelva.

`./gradlew clean build` debe terminar en `BUILD SUCCESSFUL`.

## Estructura de paquetes

```
com.example.llm_gateway_resilience
├── LlmGatewayResilienceApplication   (main)
├── RestClientConfig                  (@Bean RestClient)
├── client/
│   └── GroqApiClient      (llamada real a Groq /chat/completions, con @Retry + @CircuitBreaker)
├── controller/
│   ├── LlmGatewayController      (POST /api/v1/llm/chat)
│   └── GlobalExceptionHandler    (manejo de errores HTTP de Groq)
├── service/
│   └── LlmOrchestratorService    (delega en GroqApiClient)
└── model/
    ├── ChatRequest, ChatResponse, TokenUsage, ErrorResponse
```

## Flujo de la petición

`POST /api/v1/llm/chat` con body `ChatRequest` (`prompt`, `maxTokens`).

- Prompt vacío/`null` → 400 Bad Request (validación manual en el controller).
- Prompt válido → `GroqApiClient.generateResponse()` con retry + circuit breaker.
- Groq 401/429 → `GlobalExceptionHandler` devuelve error descriptivo.
- Circuit abierto → fallback con mensaje de degradación.

## Configuración / secretos

- `application.yml` define `groq.api.key/base-url/model`, resuelto desde `${GROQ_API_KEY}`.
- La key real vive en `.env` en la raíz (**no versionar** — ya está en `.gitignore`).

## Observabilidad

- Prometheus scraping en `/actuator/prometheus` (puerto 8085).
- `docker compose up -d` levanta Prometheus (9091) y Grafana (3001).
- Dashboard recomendado: ID `11378` en Grafana.
- La etiqueta `application=llm-gateway-resilience` viene de `management.metrics.tags.application` en `application.yml`.

## Deuda técnica conocida (no arreglar sin que se pida explícitamente)

- `RestClientConfig` crea el `RestClient` sin timeouts ni interceptores de logging.
- El coste en `TokenUsage` siempre es `0.0` — no hay lógica de precio por token para Groq.
