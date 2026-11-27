package com.example.controledeacesso.service;

import com.example.controledeacesso.dispositivos.BloqueioGeralService;
import com.example.controledeacesso.entity.Catraca;
import com.example.controledeacesso.entity.Liberacao;
import com.example.controledeacesso.repositories.CatracaRepository;
import com.example.controledeacesso.repositories.LiberacaoRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Liberacao manual de catraca.
 *
 * Fluxo: o operador pede a liberacao no front-end (POST /catracas/{id}/liberar); o tablet da
 * catraca consulta a API a cada ~1,5 s (POST /catracas/{id}/liberacao/confirmar), recebe a
 * liberacao pendente, mostra "ACESSO LIBERADO" e ela passa a "confirmada". Se nenhum tablet
 * buscar dentro do prazo, ela vira "expirada" (a catraca nao abre horas depois por engano).
 *
 * A liberacao pendente de cada catraca fica em memoria, para o polling do tablet nao bater no
 * banco a cada consulta; o historico (auditoria) fica na tabela liberacao_catraca.
 */
@Service
public class LiberacaoService {

    private static final int RECENTES_EM_MEMORIA = 200;

    private final CatracaRepository catracaRepository;
    private final LiberacaoRepository liberacaoRepository;
    private final Duration validade;
    private final Clock clock;
    private final BloqueioGeralService bloqueioGeral;

    private final Map<Long, Liberacao> pendentesPorCatraca = new HashMap<>();
    private final Map<Long, Liberacao> recentesPorId = new LinkedHashMap<>(16, 0.75f, false) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, Liberacao> eldest) {
            return size() > RECENTES_EM_MEMORIA;
        }
    };

    public LiberacaoService(CatracaRepository catracaRepository,
                            LiberacaoRepository liberacaoRepository,
                            @Value("${catraca.liberacao.validade-segundos:30}") long validadeSegundos,
                            Optional<Clock> clock,
                            BloqueioGeralService bloqueioGeral) {
        this.catracaRepository = catracaRepository;
        this.liberacaoRepository = liberacaoRepository;
        this.validade = Duration.ofSeconds(validadeSegundos);
        this.clock = clock.orElse(Clock.systemUTC());
        this.bloqueioGeral = bloqueioGeral;
    }

    public List<Catraca> listarCatracas() {
        return catracaRepository.findAll();
    }

    public Catraca cadastrarCatraca(String nome, String local) {
        if (nome == null || nome.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe o nome da catraca.");
        }
        return catracaRepository.save(nome.trim(), local == null ? null : local.trim());
    }

    public synchronized Liberacao liberar(Long catracaId, String operador, String motivo) {
        if (bloqueioGeral.ativo()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Bloqueio geral ativo: as liberações manuais estão suspensas.");
        }
        Catraca catraca = catracaRepository.findById(catracaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Catraca não encontrada."));

        Liberacao anterior = pendentesPorCatraca.remove(catracaId);
        if (anterior != null) {
            // Um novo pedido substitui o anterior que o tablet ainda nao buscou
            salvarStatus(anterior.expirada());
        }

        Instant agora = clock.instant().truncatedTo(ChronoUnit.MILLIS);
        Liberacao nova = liberacaoRepository.insert(new Liberacao(
                null, catraca.id(), catraca.nome(), limpar(operador), limpar(motivo),
                agora, agora.plus(validade), null, Liberacao.PENDENTE));

        pendentesPorCatraca.put(catracaId, nova);
        recentesPorId.put(nova.id(), nova);
        return nova;
    }

    /**
     * Chamado pelo tablet da catraca. Devolve a liberacao pendente (marcando-a como confirmada)
     * ou vazio se nao houver nenhuma valida.
     */
    public synchronized Optional<Liberacao> confirmar(Long catracaId) {
        Liberacao pendente = pendentesPorCatraca.remove(catracaId);
        if (pendente == null) {
            return Optional.empty();
        }
        Instant agora = clock.instant().truncatedTo(ChronoUnit.MILLIS);
        if (pendente.expirou(agora)) {
            salvarStatus(pendente.expirada());
            return Optional.empty();
        }
        Liberacao confirmada = pendente.confirmada(agora);
        salvarStatus(confirmada);
        return Optional.of(confirmada);
    }

    public synchronized Optional<Liberacao> buscar(Long id) {
        Instant agora = clock.instant();
        Liberacao emMemoria = recentesPorId.get(id);
        if (emMemoria != null) {
            return Optional.of(emMemoria.comStatusAtualizado(agora));
        }
        return liberacaoRepository.findById(id).map(l -> l.comStatusAtualizado(agora));
    }

    public List<Liberacao> listarRecentes(Integer limite) {
        int limiteEfetivo = limite == null ? 50 : Math.max(1, Math.min(limite, 500));
        Instant agora = clock.instant();
        return liberacaoRepository.findRecentes(limiteEfetivo).stream()
                .map(l -> l.comStatusAtualizado(agora))
                .toList();
    }

    private void salvarStatus(Liberacao liberacao) {
        recentesPorId.put(liberacao.id(), liberacao);
        liberacaoRepository.atualizarStatus(liberacao.id(), liberacao.status(), liberacao.confirmadoEm());
    }

    private static String limpar(String texto) {
        return texto == null || texto.isBlank() ? null : texto.trim();
    }
}
