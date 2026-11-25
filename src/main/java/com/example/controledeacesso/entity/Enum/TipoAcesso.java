package com.example.controledeacesso.entity.Enum;

import java.util.Locale;

public enum TipoAcesso {
    ENTRADA, SAIDA;

    /** Valor gravado na coluna logs.tipo (a face_api grava em minusculas: "entrada"/"saida"). */
    public String valorBanco() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Converte o texto do banco ou de um parametro, sem diferenciar maiusculas de minusculas. */
    public static TipoAcesso deTexto(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        return TipoAcesso.valueOf(valor.trim().toUpperCase(Locale.ROOT));
    }
}
