# llm-gateway-resilience

Gateway en Spring Boot que expone un endpoint de chat y puede responder con un mock o con la
API real de Groq, con capa de resiliencia (Resilience4j) prevista para envolver el cliente real.

## Origen del proyecto

Este proyecto reemplaza a uno anterior ubicado en
`Proyecto_Number4_july_2026\llm-gateway-resilience`, descartado por usar Gradle 9.5.1
(incompatible con `io.spring.dependency-management`). Este se generó desde cero con el ZIP de
start.spring.io (Spring Initializr) el 2026-07-05, con versiones reales y coherentes.

## Build

- Gradle wrapper: **8.14.5**
- Plugins: `org.springframework.boot:3.5.16` + `io.spring.dependency-management:1.1.7`
- Java toolchain: **21**
- Paquete raíz: `com.example.llm_gateway_resilience`

Dependencias añadidas manualmente sobre el scaffold de Initializr (que solo trae `starter` +
lombok + devtools + test):
- `spring-boot-starter-web`
- `spring-boot-starter-actuator` + `micrometer-registry-prometheus`
- `resilience4j-spring-boot3:2.2.0` + `spring-boot-starter-aop`
- `springdoc-openapi-starter-webmvc-ui:2.5.0`
- `me.paulschwarz:spring-dotenv:4.0.0` — Spring no lee `.env` de forma nativa; esta librería lo
  carga al arrancar para que `${GROQ_API_KEY}` en `application.yml` se resuelva.

`./gradlew clean build` debe terminar en `BUILD SUCCESSFUL`. Si no, algo se ha roto.

## Estructura de paquetes

```
com.example.llm_gateway_resilience
├── LlmGatewayResilienceApplication   (main)
├── RestClientConfig                  (@Bean RestClient, sin timeouts configurados aún)
├── client/
│   ├── LlmClient          (interfaz, patrón Strategy)
│   ├── MockApiClient      (simula latencia + tokens estimados, respuesta ficticia)
│   └── GroqApiClient      (llamada real a Groq /chat/completions)
├── controller/
│   └── LlmGatewayController   (POST /api/v1/llm/chat)
├── service/
│   └── LlmOrchestratorService (decide mock vs real según header X-Execution-Mode)
└── model/
    ├── ChatRequest, ChatResponse, TokenUsage
```

**Cuidado**: `LlmGatewayController` estuvo mal anidado dentro de `model/controller/` en algún
punto. Si reaparece un subpaquete `model.controller`, es un error — el paquete correcto es
`controller/`, hermano de `model`, `service` y `client`.

## Flujo de la petición

`POST /api/v1/llm/chat` con body `ChatRequest` (`prompt`, `maxTokens`).
- Sin cabecera `X-Execution-Mode`, o con cualquier valor distinto de `REAL` → usa `MockApiClient`.
- Con `X-Execution-Mode: REAL` → usa `GroqApiClient` (llamada real, requiere `GROQ_API_KEY` válida).

Validación de prompt vacío/`null` hecha a mano con un `if` en el controller (sin
`spring-boot-starter-validation`, decisión deliberada por simplicidad).

## Configuración / secretos

- `application.yml` define `groq.api.key/base-url/model`, resuelto desde `${GROQ_API_KEY}`.
- La key real vive en `.env` en la raíz (**no versionar** — ya está en `.gitignore`). El proyecto
  todavía no es un repo git.
- `src/main/java/.../notes.txt` es un archivo suelto sin extensión de código dentro del árbol de
  fuentes — quedó vacío tras migrar la key al `.env`. Se puede borrar sin impacto.

## Deuda técnica conocida (no arreglar sin que se pida explícitamente)

- `GroqApiClient.parseGroqResponse` no valida la forma del JSON de Groq: un error de la API
  (401, 429, rate limit, etc.) explota con `NullPointerException`/`ClassCastException` en vez de
  un error controlado. Tampoco maneja `RestClientResponseException` de respuestas 4xx/5xx.
- Las dependencias de Resilience4j/Actuator/AOP están en el `build.gradle` pero **no hay ninguna
  anotación `@CircuitBreaker`/`@Retry` ni configuración en `application.yml`** todavía — el
  cliente real se llama sin ninguna protección de resiliencia pese al nombre del proyecto.
- `RestClientConfig` crea el `RestClient` sin timeouts ni interceptores de logging.
