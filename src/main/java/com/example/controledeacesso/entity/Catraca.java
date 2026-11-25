package com.example.controledeacesso.entity;

/** Tabela "catraca" no Cloudflare D1 (colunas: id, nome, local). Cada tablet/totem e configurado com o id da sua catraca. */
public record Catraca(Long id, String nome, String local) {
}
