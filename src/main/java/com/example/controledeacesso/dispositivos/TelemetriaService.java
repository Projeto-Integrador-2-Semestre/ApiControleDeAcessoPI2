package com.example.controledeacesso.dispositivos;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Telemetria dos totens, sem nenhuma mudanca no tablet: ele ja consulta a API a cada ~1,5 s
 * (POST /catracas/{id}/liberacao/confirmar) e cada consulta conta como sinal de vida.
 * Sem contato ha mais de {@link #LIMITE_ONLINE}, o totem e considerado offline.
 *
 * Fica so em memoria de proposito: gravar no D1 a cada 1,5 s por tablet seria caro e lento.
 * Depois de um reinicio da API, cada totem volta a aparecer em ate 2 s.
 */
@Component
public class TelemetriaService {

    public static final Duration LIMITE_ONLINE = Duration.ofSeconds(10);
    private static final int EVENTOS_POR_CATRACA = 50;
    // Protege a memoria contra ids inventados (a rota de consulta do tablet e aberta)
    private static final int MAXIMO_CATRACAS = 200;

    public record Contato(Instant em, String ip) {
    }

    /** Evento do syslog da estacao. tipo: facial | rele | heartbeat | alerta (classe CSS do painel). */
    public record Evento(Instant dataHora, String tipo, String titulo, String texto) {
    }

    private final Map<Long, Contato> contatos = new ConcurrentHashMap<>();
    private final Map<Long, Deque<Evento>> eventos = new ConcurrentHashMap<>();
    private final Clock clock;

    public TelemetriaService(Optional<Clock> clock) {
        this.clock = clock.orElse(Clock.systemUTC());
    }

    /** Chamado a cada consulta do tablet. Registra no syslog quando ele conecta ou volta depois de sumir. */
    public void registrarContato(long catracaId, String ip) {
        if (!contatos.containsKey(catracaId) && contatos.size() >= MAXIMO_CATRACAS) {
            return;
        }
        Instant agora = agora();
        Contato anterior = contatos.put(catracaId, new Contato(agora, ip));
        if (anterior == null) {
            registrarEvento(catracaId, "heartbeat", "[HEARTBEAT] TOTEM CONECTADO",
                    "Primeira consulta do totem desde que a API subiu" + (ip == null ? "." : " (IP " + ip + ")."));
        } else {
            long segundos = Duration.between(anterior.em(), agora).toSeconds();
            if (segundos > LIMITE_ONLINE.toSeconds()) {
                registrarEvento(catracaId, "alerta", "[HEARTBEAT] TOTEM RECONECTADO",
                        "O totem ficou " + segundos + " s sem consultar a API.");
            }
        }
    }

    public Optional<Contato> ultimoContato(long catracaId) {
        return Optional.ofNullable(contatos.get(catracaId));
    }

    public boolean online(long catracaId) {
        Contato c = contatos.get(catracaId);
        return c != null && Duration.between(c.em(), agora()).compareTo(LIMITE_ONLINE) <= 0;
    }

    /** Segundos desde o ultimo contato, ou null se o totem nunca consultou a API. */
    public Long segundosDesdeContato(long catracaId) {
        Contato c = contatos.get(catracaId);
        return c == null ? null : Duration.between(c.em(), agora()).toSeconds();
    }

    public void registrarEvento(long catracaId, String tipo, String titulo, String texto) {
        Deque<Evento> fila = eventos.computeIfAbsent(catracaId, id -> new ArrayDeque<>());
        synchronized (fila) {
            fila.addFirst(new Evento(agora(), tipo, titulo, texto));
            while (fila.size() > EVENTOS_POR_CATRACA) {
                fila.removeLast();
            }
        }
    }

    public List<Evento> eventos(long catracaId) {
        Deque<Evento> fila = eventos.get(catracaId);
        if (fila == null) {
            return List.of();
        }
        synchronized (fila) {
            return List.copyOf(fila);
        }
    }

    private Instant agora() {
        return clock.instant().truncatedTo(ChronoUnit.MILLIS);
    }
}
