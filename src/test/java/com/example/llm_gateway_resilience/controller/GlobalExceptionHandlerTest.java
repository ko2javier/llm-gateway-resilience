package com.example.llm_gateway_resilience.controller;

import com.example.llm_gateway_resilience.model.ErrorResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void deberiaDevolver401ConMensajeClaroCuandoKeyEsInvalida() {
        // ARRANGE: simulamos la excepción que lanzaría RestClient si Groq responde 401
        HttpClientErrorException excepcion = HttpClientErrorException.create(
                HttpStatus.UNAUTHORIZED,
                "Unauthorized",
                null,
                null,
                null
        );

        // ACT
        ResponseEntity<ErrorResponse> resultado = handler.handleGroqClientError(excepcion);

        // ASSERT
        assertEquals(HttpStatus.UNAUTHORIZED, resultado.getStatusCode());
        assertEquals(401, resultado.getBody().getStatus());
        assertTrue(resultado.getBody().getError().contains("API key"));
    }

    @Test
    void deberiaDevolver429ConMensajeDeRateLimit() {
        HttpClientErrorException excepcion = HttpClientErrorException.create(
                HttpStatus.TOO_MANY_REQUESTS,
                "Too Many Requests",
                null,
                null,
                null
        );

        ResponseEntity<ErrorResponse> resultado = handler.handleGroqClientError(excepcion);

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, resultado.getStatusCode());
        assertEquals(429, resultado.getBody().getStatus());
        assertTrue(resultado.getBody().getError().contains("límite de peticiones"));
    }

    @Test
    void deberiaDevolver502CuandoGroqTieneErrorDeServidor() {
        HttpServerErrorException excepcion = HttpServerErrorException.create(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Service Unavailable",
                null,
                null,
                null
        );

        ResponseEntity<ErrorResponse> resultado = handler.handleGroqServerError(excepcion);

        assertEquals(HttpStatus.BAD_GATEWAY, resultado.getStatusCode());
        assertEquals(502, resultado.getBody().getStatus());
    }
}