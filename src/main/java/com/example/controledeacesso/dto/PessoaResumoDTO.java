package com.example.controledeacesso.dto;

import java.time.Instant;

/**
 * Pessoa sem a foto (base64), para listagens leves no front-end.
 *
 * @param status       "liberado" ou "bloqueado"
 * @param dataCadastro instante em UTC; nulo para cadastros antigos
 */
public record PessoaResumoDTO(String cpf, String nome, boolean possuiFoto,
                              String status, String motivoBloqueio, Instant dataCadastro) {
}
