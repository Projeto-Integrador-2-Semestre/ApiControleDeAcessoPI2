package com.example.controledeacesso.entity.Enum;

import java.util.Locale;

/** Situacao de acesso da pessoa. Bloqueada, a face_api reconhece o rosto mas nao libera a catraca. */
public enum StatusPessoa {
    LIBERADO, BLOQUEADO;

    /** Valor gravado na coluna pessoa.status ("liberado"/"bloqueado"). */
    public String valorBanco() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Converte o texto do banco ou da requisicao. Nulo/vazio vira LIBERADO (linhas antigas da tabela). */
    public static StatusPessoa deTexto(String valor) {
        if (valor == null || valor.isBlank()) {
            return LIBERADO;
        }
        return StatusPessoa.valueOf(valor.trim().toUpperCase(Locale.ROOT));
    }
}
