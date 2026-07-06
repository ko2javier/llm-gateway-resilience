package com.example.llm_gateway_resilience.controller;


import com.example.llm_gateway_resilience.model.ChatRequest;
import com.example.llm_gateway_resilience.model.ChatResponse;
import com.example.llm_gateway_resilience.service.LlmOrchestratorService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/llm")
public class LlmGatewayController {

    private final LlmOrchestratorService orchestratorService;

    public LlmGatewayController(LlmOrchestratorService orchestratorService) {
        this.orchestratorService = orchestratorService;
    }

    /*
    @RequestHeader(required = false): así, si el cliente no manda la cabecera
     X-Execution-Mode (el caso normal, modo mock por defecto), no explota con un 400 automático
     de Spring — simplemente llega null al orchestrator, que ya lo trata como "no es REAL"
     gracias al equalsIgnoreCase.
     */
    @PostMapping("/chat")
    public ResponseEntity<ChatResponse> chat(
            @RequestBody ChatRequest request,
            @RequestHeader(value = "X-Execution-Mode", required = false) String executionMode
    ) {

        /*
        Validación del prompt vacío aquí, con un simple if: tal como hablamos antes,
        sin meter spring-boot-starter-validation ni anotaciones.
        Si el prompt está vacío o es null, devolvemos un 400 Bad Request sin cuerpo,
        sin llegar siquiera a tocar el orchestrator.
         */

        if (request.getPrompt() == null || request.getPrompt().isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        ChatResponse response = orchestratorService.processRequest(request, executionMode);
        /*
        ResponseEntity<ChatResponse>: te da control explícito sobre
        el código HTTP de la respuesta (200, 400, etc.), en vez de
        que Spring decida por ti devolviendo siempre 200.
         */
        return ResponseEntity.ok(response);
    }
}