package com.example.controledeacesso.dto;

/** Corpo opcional de POST /catracas/{id}/liberar. */
public record LiberarCatracaRequest(String operador, String motivo) {
}
