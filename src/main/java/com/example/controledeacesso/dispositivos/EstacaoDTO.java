package com.example.controledeacesso.dispositivos;

import java.time.Instant;

/**
 * Estacao de passagem no monitoramento: a catraca e o totem (tablet) configurado com o id dela,
 * mais o servico de reconhecimento (face_api), que e compartilhado por todas as estacoes.
 *
 * @param statusGeral   operacional (totem online e reconhecimento pronto) | sincronizando
 *                      (face_api carregando rostos) | alerta (totem sem contato ou face_api fora do ar)
 * @param passagensHoje passagens registradas hoje (fuso de Sao Paulo) por esta catraca
 */
public record EstacaoDTO(
        Long id,
        String nome,
        String local,
        String statusGeral,
        Totem totem,
        Reconhecimento reconhecimento,
        long passagensHoje,
        Instant ultimaPassagem,
        boolean bloqueioGeral
) {

    /** @param ip endereco visto pela API (atras do proxy do Render, e o IP publico da rede do tablet) */
    public record Totem(boolean online, Instant ultimoContato, Long segundosDesdeContato, String ip) {
    }

    /** @param status reconhecendo | carregando | offline | nao_configurado */
    public record Reconhecimento(String status, Integer rostosEmCache, Boolean antiSpoofing, Long latenciaMs) {
    }
}
