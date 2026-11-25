package com.example.controledeacesso.dto;

/** Pessoa sem a foto (base64), para listagens leves no front-end. */
public record PessoaResumoDTO(String cpf, String nome, boolean possuiFoto) {
}
