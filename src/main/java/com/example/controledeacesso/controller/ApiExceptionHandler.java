package com.example.controledeacesso.controller;

import com.example.controledeacesso.db.D1Exception;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

/** Respostas de erro em JSON ({"detail": "..."}) para o front-end conseguir exibir a mensagem. */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> status(ResponseStatusException e) {
        String mensagem = e.getReason() == null ? e.getStatusCode().toString() : e.getReason();
        return ResponseEntity.status(e.getStatusCode()).body(Map.of("detail", mensagem));
    }

    @ExceptionHandler(D1Exception.class)
    public ResponseEntity<Map<String, String>> bancoIndisponivel(D1Exception e) {
        log.error("Erro no Cloudflare D1", e);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("detail", "Banco de dados indisponível no momento."));
    }
}
