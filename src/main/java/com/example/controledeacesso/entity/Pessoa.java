package com.example.controledeacesso.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Tabela "pessoa" no Cloudflare D1 (colunas: cpf, nome, foto, status, motivo_bloqueio, data_cadastro).
 * status e "liberado" ou "bloqueado"; data_cadastro fica nula para quem foi cadastrado antes dessa coluna existir.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Pessoa {

    private String cpf;

    private String nome;

    private String foto;

    @Builder.Default
    private String status = "liberado";

    private String motivoBloqueio;

    private Instant dataCadastro;

}
