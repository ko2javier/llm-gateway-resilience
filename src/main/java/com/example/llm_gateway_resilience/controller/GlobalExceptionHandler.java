package com.example.llm_gateway_resilience.controller;

import com.example.llm_gateway_resilience.model.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(HttpClientErrorException.class)
    public ResponseEntity<ErrorResponse> handleGroqClientError(HttpClientErrorException ex) {
        String message = switch (ex.getStatusCode().value()) {
            case 401 -> "La API key de Groq no es válida o falta.";
            case 429 -> "Se ha superado el límite de peticiones de Groq (rate limit). Intenta de nuevo en unos segundos.";
            default -> "Groq rechazó la petición: " + ex.getStatusText();
        };

        ErrorResponse errorResponse = new ErrorResponse(ex.getStatusCode().value(), message);
        return ResponseEntity.status(ex.getStatusCode()).body(errorResponse);
    }

    @ExceptionHandler(HttpServerErrorException.class)
    public ResponseEntity<ErrorResponse> handleGroqServerError(HttpServerErrorException ex) {
        ErrorResponse errorResponse = new ErrorResponse(
                HttpStatus.BAD_GATEWAY.value(),
                "El servicio de Groq no está disponible en este momento."
        );
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(errorResponse);
    }

    @ExceptionHandler({NullPointerException.class, ClassCastException.class})
    public ResponseEntity<ErrorResponse> handleParsingError(RuntimeException ex) {
        ErrorResponse errorResponse = new ErrorResponse(
                HttpStatus.BAD_GATEWAY.value(),
                "Respuesta inesperada del proveedor de IA. No se pudo procesar."
        );
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(errorResponse);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex) {
        ErrorResponse errorResponse = new ErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "Error interno inesperado: " + ex.getMessage()
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    }
}