package com.example.controledeacesso.entity;

import java.time.Instant;

/**
 * Liberacao manual de catraca pedida por um operador no front-end (tabela "liberacao_catraca").
 * Fica "pendente" ate o tablet da catraca busca-la (status "confirmada") ou ate passar o prazo ("expirada").
 */
public record Liberacao(
        Long id,
        Long catracaId,
        String catracaNome,
        String operador,
        String motivo,
        Instant solicitadoEm,
        Instant expiraEm,
        Instant confirmadoEm,
        String status
) {

    public static final String PENDENTE = "pendente";
    public static final String CONFIRMADA = "confirmada";
    public static final String EXPIRADA = "expirada";

    public boolean expirou(Instant agora) {
        return PENDENTE.equals(status) && expiraEm != null && agora.isAfter(expiraEm);
    }

    public Liberacao comId(Long novoId) {
        return new Liberacao(novoId, catracaId, catracaNome, operador, motivo, solicitadoEm, expiraEm, confirmadoEm, status);
    }

    public Liberacao confirmada(Instant quando) {
        return new Liberacao(id, catracaId, catracaNome, operador, motivo, solicitadoEm, expiraEm, quando, CONFIRMADA);
    }

    public Liberacao expirada() {
        return new Liberacao(id, catracaId, catracaNome, operador, motivo, solicitadoEm, expiraEm, null, EXPIRADA);
    }

    /** Status real considerando o prazo (uma linha "pendente" antiga no banco ja esta expirada). */
    public Liberacao comStatusAtualizado(Instant agora) {
        return expirou(agora) ? expirada() : this;
    }
}
