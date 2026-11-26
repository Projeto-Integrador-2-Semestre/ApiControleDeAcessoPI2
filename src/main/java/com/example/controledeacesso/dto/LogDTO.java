package com.example.controledeacesso.dto;

import com.example.controledeacesso.entity.Log;

import java.time.Instant;
import java.time.ZoneOffset;

/**
 * Registro de entrada/saida devolvido ao front-end (sem a foto da pessoa).
 *
 * @param tipo      "entrada" ou "saida"
 * @param dataHora  instante em UTC (ISO 8601, ex.: 2026-11-13T00:52:58.377Z)
 * @param catracaId catraca do totem que registrou; nulo nos logs gravados antes da face_api informar
 */
public record LogDTO(Long id, String cpf, String nome, String tipo, Instant dataHora,
                     Long catracaId, String catracaNome, String catracaLocal) {

    public static LogDTO de(Log log) {
        return new LogDTO(
                log.getId(),
                log.getPessoa() == null ? null : log.getPessoa().getCpf(),
                log.getPessoa() == null ? null : log.getPessoa().getNome(),
                log.getTipoAcesso() == null ? null : log.getTipoAcesso().valorBanco(),
                log.getDataHora() == null ? null : log.getDataHora().toInstant(ZoneOffset.UTC),
                log.getCatracaId(),
                log.getCatracaNome(),
                log.getCatracaLocal()
        );
    }
}
