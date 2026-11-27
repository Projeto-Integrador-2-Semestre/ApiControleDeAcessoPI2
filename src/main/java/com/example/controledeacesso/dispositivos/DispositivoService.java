package com.example.controledeacesso.dispositivos;

import com.example.controledeacesso.entity.Catraca;
import com.example.controledeacesso.entity.Liberacao;
import com.example.controledeacesso.entity.Log;
import com.example.controledeacesso.integracao.FaceApiNotifier;
import com.example.controledeacesso.repositories.CatracaRepository;
import com.example.controledeacesso.repositories.LiberacaoRepository;
import com.example.controledeacesso.repositories.LogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Monitoramento das estacoes (GET /dispositivos/...). O que e medido de verdade:
 *  - totem online/offline, ultimo contato e IP: pela consulta periodica do tablet ({@link TelemetriaService});
 *  - reconhecimento: pelo GET /health da face_api (status, rostos em cache, latencia);
 *  - passagens: pela tabela logs (coluna catraca_id).
 * Comandos que dependem do rele da catraca (testar giro, reiniciar) ainda nao existem no totem.
 */
@Service
public class DispositivoService {

    private static final Logger log = LoggerFactory.getLogger(DispositivoService.class);
    /** "Hoje" no fuso da unidade (o banco guarda em UTC). */
    static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");
    private static final int LIMITE_SYSLOG = 40;

    private final CatracaRepository catracas;
    private final LogRepository logs;
    private final LiberacaoRepository liberacoes;
    private final TelemetriaService telemetria;
    private final FaceApiNotifier faceApi;
    private final BloqueioGeralService bloqueioGeral;
    private final Clock clock;

    public DispositivoService(CatracaRepository catracas, LogRepository logs, LiberacaoRepository liberacoes,
                              TelemetriaService telemetria, FaceApiNotifier faceApi,
                              BloqueioGeralService bloqueioGeral, Optional<Clock> clock) {
        this.catracas = catracas;
        this.logs = logs;
        this.liberacoes = liberacoes;
        this.telemetria = telemetria;
        this.faceApi = faceApi;
        this.bloqueioGeral = bloqueioGeral;
        this.clock = clock.orElse(Clock.systemUTC());
    }

    public List<EstacaoDTO> listar() {
        // Uma consulta a face_api e uma ao banco para todas as estacoes
        FaceApiNotifier.Saude saude = faceApi.consultarSaude();
        Map<Long, LogRepository.ResumoCatraca> resumo = logs.resumoPorCatraca(inicioDeHojeUtc());
        boolean bloqueio = bloqueioGeral.ativo();
        return catracas.findAll().stream()
                .map(c -> montar(c, saude, resumo.get(c.id()), bloqueio))
                .toList();
    }

    public EstacaoDTO buscar(long id) {
        Catraca catraca = catraca(id);
        return montar(catraca, faceApi.consultarSaude(),
                logs.resumoPorCatraca(inicioDeHojeUtc()).get(id), bloqueioGeral.ativo());
    }

    /** Resposta do botao "Ping": quando o totem falou com a API pela ultima vez e quanto a face_api demora. */
    public record Ping(boolean totemOnline, Long segundosDesdeContato, boolean reconhecimentoOnline,
                       Long latenciaReconhecimentoMs) {
    }

    public Ping ping(long id) {
        Catraca catraca = catraca(id);
        FaceApiNotifier.Saude saude = faceApi.consultarSaude();
        Ping ping = new Ping(telemetria.online(id), telemetria.segundosDesdeContato(id), saude.online(), saude.latenciaMs());
        telemetria.registrarEvento(id, ping.totemOnline() ? "heartbeat" : "alerta", "[PING] " + catraca.nome(),
                (ping.segundosDesdeContato() == null ? "Totem nunca consultou a API."
                        : "Último contato do totem há " + ping.segundosDesdeContato() + " s.")
                        + (saude.online() ? " face_api respondeu em " + saude.latenciaMs() + " ms."
                        : " face_api sem resposta."));
        return ping;
    }

    /** Botao "Sincronizar": pede para a face_api reler as fotos agora e devolve o resumo dela. */
    public Map<String, Object> sincronizarFacial(long id) {
        catraca(id);
        if (!faceApi.isConfigurado()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "A URL da face_api não está configurada na API (FACE_API_URL).");
        }
        try {
            Map<String, Object> resumo = faceApi.recarregarAgora();
            telemetria.registrarEvento(id, "facial", "[SYNC] BASE DE ROSTOS ATUALIZADA",
                    "face_api releu o cadastro: " + resumo.getOrDefault("embeddings_em_cache", "?") + " rostos em cache, "
                            + resumo.getOrDefault("novos_ou_atualizados", 0) + " novos ou atualizados.");
            return resumo;
        } catch (RuntimeException e) {
            log.warn("Falha ao sincronizar a face_api: {}", e.getMessage());
            telemetria.registrarEvento(id, "alerta", "[SYNC] FALHA", "A face_api não respondeu ao pedido de sincronização.");
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "A face_api não respondeu. Ela pode estar iniciando; tente de novo em instantes.");
        }
    }

    /** Testar giro / reiniciar: dependem do acionamento fisico da catraca, que o totem ainda nao faz. */
    public void comandoNaoSuportado(long id, String comando) {
        catraca(id);
        throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED,
                "O totem ainda não executa \"" + comando + "\": depende do acionamento físico da catraca (relé).");
    }

    /** Syslog da estacao: passagens, liberacoes manuais e eventos do totem, mais recentes primeiro. */
    public List<TelemetriaService.Evento> syslog(long id) {
        catraca(id);
        List<TelemetriaService.Evento> eventos = new ArrayList<>(telemetria.eventos(id));
        for (Log l : logs.findByCatraca(id, 15)) {
            String tipo = l.getTipoAcesso() == null ? "PASSAGEM" : l.getTipoAcesso().name();
            String nome = l.getPessoa() == null || l.getPessoa().getNome() == null ? "Pessoa removida" : l.getPessoa().getNome();
            eventos.add(new TelemetriaService.Evento(
                    l.getDataHora() == null ? null : l.getDataHora().toInstant(ZoneOffset.UTC),
                    "facial", "[FACIAL_MATCH] " + tipo,
                    nome + " (CPF " + mascarar(l.getPessoa() == null ? null : l.getPessoa().getCpf())
                            + ") reconhecida; passagem registrada."));
        }
        for (Liberacao lib : liberacoes.findRecentesDaCatraca(id, 10)) {
            Liberacao atual = lib.comStatusAtualizado(clock.instant());
            eventos.add(new TelemetriaService.Evento(atual.solicitadoEm(), "rele",
                    "[LIBERACAO_MANUAL] " + atual.status().toUpperCase(),
                    "Pedida por " + (atual.operador() == null ? "operador" : atual.operador())
                            + (atual.motivo() == null ? "." : ". Motivo: " + atual.motivo() + ".")));
        }
        return eventos.stream()
                .filter(e -> e.dataHora() != null)
                .sorted(Comparator.comparing(TelemetriaService.Evento::dataHora).reversed())
                .limit(LIMITE_SYSLOG)
                .toList();
    }

    public BloqueioGeralService.Estado bloqueioGeral() {
        return bloqueioGeral.estado();
    }

    public BloqueioGeralService.Estado alterarBloqueioGeral(boolean ativo, String operador) {
        BloqueioGeralService.Estado estado = bloqueioGeral.alterar(ativo, operador);
        for (Catraca c : catracas.findAll()) {
            telemetria.registrarEvento(c.id(), ativo ? "alerta" : "heartbeat",
                    ativo ? "[BLOQUEIO_GERAL] ATIVADO" : "[BLOQUEIO_GERAL] DESATIVADO",
                    (ativo ? "Reconhecimento e liberações manuais suspensos" : "Acesso normalizado")
                            + (operador == null ? "." : " por " + operador + "."));
        }
        return estado;
    }

    // ----- montagem -----

    private EstacaoDTO montar(Catraca c, FaceApiNotifier.Saude saude, LogRepository.ResumoCatraca resumo, boolean bloqueio) {
        long id = c.id();
        Optional<TelemetriaService.Contato> contato = telemetria.ultimoContato(id);
        EstacaoDTO.Totem totem = new EstacaoDTO.Totem(
                telemetria.online(id),
                contato.map(TelemetriaService.Contato::em).orElse(null),
                telemetria.segundosDesdeContato(id),
                contato.map(TelemetriaService.Contato::ip).orElse(null));
        EstacaoDTO.Reconhecimento reconhecimento = new EstacaoDTO.Reconhecimento(
                statusReconhecimento(saude), saude.rostosEmCache(), saude.antiSpoofing(), saude.latenciaMs());
        return new EstacaoDTO(id, c.nome(), c.local(), statusGeral(totem, reconhecimento.status()), totem, reconhecimento,
                resumo == null ? 0 : resumo.passagensDesde(),
                resumo == null || resumo.ultimaPassagem() == null ? null : resumo.ultimaPassagem().toInstant(ZoneOffset.UTC),
                bloqueio);
    }

    static String statusReconhecimento(FaceApiNotifier.Saude saude) {
        if (!saude.configurada()) return "nao_configurado";
        if (!saude.online()) return "offline";
        return saude.pronta() ? "reconhecendo" : "carregando";
    }

    static String statusGeral(EstacaoDTO.Totem totem, String reconhecimento) {
        if (!totem.online()) return "alerta";
        return switch (reconhecimento) {
            case "reconhecendo" -> "operacional";
            case "carregando" -> "sincronizando";
            default -> "alerta";
        };
    }

    private java.time.LocalDateTime inicioDeHojeUtc() {
        return LocalDate.now(clock.withZone(FUSO)).atStartOfDay(FUSO)
                .withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
    }

    private Catraca catraca(long id) {
        return catracas.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Estação não encontrada."));
    }

    private static String mascarar(String cpf) {
        if (cpf == null || cpf.length() != 11) return cpf == null ? "—" : cpf;
        return cpf.substring(0, 3) + ".***." + cpf.substring(6, 9) + "-" + cpf.substring(9);
    }
}
