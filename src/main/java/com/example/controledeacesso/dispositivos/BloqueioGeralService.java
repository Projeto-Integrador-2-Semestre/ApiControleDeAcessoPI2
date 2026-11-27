package com.example.controledeacesso.dispositivos;

import com.example.controledeacesso.integracao.FaceApiNotifier;
import com.example.controledeacesso.repositories.ConfiguracaoRepository;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

/**
 * Bloqueio geral de emergencia. Ativo, ele:
 *  - faz a face_api recusar todos os rostos (motivo "bloqueio_geral"); ela le a chave na tabela configuracao;
 *  - impede novas liberacoes manuais de catraca pelo painel.
 * O travamento fisico das catracas depende do acionamento do rele (item R3), que ainda nao existe.
 */
@Service
public class BloqueioGeralService {

    public static final String CHAVE = "bloqueio_geral";

    public record Estado(boolean ativo, Instant alteradoEm, String alteradoPor) {
    }

    private final ConfiguracaoRepository configuracoes;
    private final FaceApiNotifier faceApi;
    private final Clock clock;
    // Lido do banco uma vez e mantido em memoria: e consultado a cada pedido de liberacao
    private volatile Estado cache;

    public BloqueioGeralService(ConfiguracaoRepository configuracoes, FaceApiNotifier faceApi, Optional<Clock> clock) {
        this.configuracoes = configuracoes;
        this.faceApi = faceApi;
        this.clock = clock.orElse(Clock.systemUTC());
    }

    public Estado estado() {
        Estado atual = cache;
        if (atual == null) {
            atual = configuracoes.buscar(CHAVE)
                    .map(c -> new Estado("true".equalsIgnoreCase(c.valor()), c.alteradoEm(), c.alteradoPor()))
                    .orElse(new Estado(false, null, null));
            cache = atual;
        }
        return atual;
    }

    public boolean ativo() {
        return estado().ativo();
    }

    public synchronized Estado alterar(boolean ativo, String operador) {
        Instant agora = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        configuracoes.salvar(CHAVE, String.valueOf(ativo), agora, operador);
        cache = new Estado(ativo, agora, operador);
        // A face_api passa a recusar (ou volta a liberar) na hora, sem esperar o ciclo de 60 s
        faceApi.pessoasAlteradas();
        return cache;
    }
}
