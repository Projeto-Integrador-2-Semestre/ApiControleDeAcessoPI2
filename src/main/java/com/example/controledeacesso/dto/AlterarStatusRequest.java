package com.example.controledeacesso.dto;

/** Corpo de PATCH /pessoa/{cpf}/status. Ex.: {"status": "bloqueado", "motivo": "Matricula trancada"} */
public record AlterarStatusRequest(String status, String motivo) {
}
