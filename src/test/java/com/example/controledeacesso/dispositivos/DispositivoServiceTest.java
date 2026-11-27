package com.example.controledeacesso.dispositivos;

import com.example.controledeacesso.entity.Catraca;
import com.example.controledeacesso.entity.Enum.TipoAcesso;
import com.example.controledeacesso.entity.Liberacao;
import com.example.controledeacesso.entity.Log;
import com.example.controledeacesso.entity.Pessoa;
import com.example.controledeacesso.integracao.FaceApiNotifier;
import com.example.controledeacesso.repositories.CatracaRepository;
import com.example.controledeacesso.repositories.LiberacaoRepository;
import com.example.controledeacesso.repositories.LogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DispositivoServiceTest {

    private static final FaceApiNotifier.Saude PRONTA = new FaceApiNotifier.Saude(true, true, true, 42, true, 230L);

    private CatracaRepository catracas;
    private LogRepository logs;
    private LiberacaoRepository liberacoes;
    private FaceApiNotifier faceApi;
    private BloqueioGeralService bloqueio;
    private TelemetriaServiceTest.RelogioTeste relogio;
    private TelemetriaService telemetria;
    private DispositivoService service;

    @BeforeEach
    void setUp() {
        catracas = mock(CatracaRepository.class);
        logs = mock(LogRepository.class);
        liberacoes = mock(LiberacaoRepository.class);
        faceApi = mock(FaceApiNotifier.class);
        bloqueio = mock(BloqueioGeralService.class);
        relogio = new TelemetriaServiceTest.RelogioTeste(Instant.parse("2026-11-27T15:00:00Z"));
        telemetria = new TelemetriaService(Optional.of(relogio));
        service = new DispositivoService(catracas, logs, liberacoes, telemetria, faceApi, bloqueio, Optional.of(relogio));

        Catraca c1 = new Catraca(1L, "Catraca 01", "Portaria Principal");
        Catraca c2 = new Catraca(2L, "Catraca 02", "Portão Docentes");
        when(catracas.findAll()).thenReturn(List.of(c1, c2));
        when(catracas.findById(1L)).thenReturn(Optional.of(c1));
        when(catracas.findById(2L)).thenReturn(Optional.of(c2));
        when(catracas.findById(99L)).thenReturn(Optional.empty());
        when(faceApi.consultarSaude()).thenReturn(PRONTA);
        when(logs.resumoPorCatraca(any())).thenReturn(Map.of(
                1L, new LogRepository.ResumoCatraca(7, LocalDateTime.of(2026, 11, 27, 14, 50))));
    }

    private static HttpStatus status(Throwable e) {
        return HttpStatus.valueOf(((ResponseStatusException) e).getStatusCode().value());
    }

    @Test
    void estacaoComTotemOnlineEReconhecimentoProntoEstaOperacional() {
        telemetria.registrarContato(1, "200.1.2.3");

        List<EstacaoDTO> estacoes = service.listar();

        EstacaoDTO e1 = estacoes.get(0);
        assertThat(e1.statusGeral()).isEqualTo("operacional");
        assertThat(e1.totem().online()).isTrue();
        assertThat(e1.totem().ip()).isEqualTo("200.1.2.3");
        assertThat(e1.reconhecimento().status()).isEqualTo("reconhecendo");
        assertThat(e1.reconhecimento().rostosEmCache()).isEqualTo(42);
        assertThat(e1.passagensHoje()).isEqualTo(7);
        assertThat(e1.ultimaPassagem()).isEqualTo(Instant.parse("2026-11-27T14:50:00Z"));

        // Totem 2 nunca consultou a API
        EstacaoDTO e2 = estacoes.get(1);
        assertThat(e2.statusGeral()).isEqualTo("alerta");
        assertThat(e2.totem().online()).isFalse();
        assertThat(e2.passagensHoje()).isZero();
    }

    @Test
    void statusGeralSegueTotemEFaceApi() {
        EstacaoDTO.Totem online = new EstacaoDTO.Totem(true, null, 1L, null);
        EstacaoDTO.Totem offline = new EstacaoDTO.Totem(false, null, 60L, null);
        assertThat(DispositivoService.statusGeral(online, "reconhecendo")).isEqualTo("operacional");
        assertThat(DispositivoService.statusGeral(online, "carregando")).isEqualTo("sincronizando");
        assertThat(DispositivoService.statusGeral(online, "offline")).isEqualTo("alerta");
        assertThat(DispositivoService.statusGeral(offline, "reconhecendo")).isEqualTo("alerta");

        assertThat(DispositivoService.statusReconhecimento(new FaceApiNotifier.Saude(false, false, false, null, null, null)))
                .isEqualTo("nao_configurado");
        assertThat(DispositivoService.statusReconhecimento(new FaceApiNotifier.Saude(true, false, false, null, null, null)))
                .isEqualTo("offline");
        assertThat(DispositivoService.statusReconhecimento(new FaceApiNotifier.Saude(true, true, false, 0, true, 900L)))
                .isEqualTo("carregando");
    }

    @Test
    void pingInformaUltimoContatoELatenciaDaFaceApi() {
        telemetria.registrarContato(1, null);
        relogio.avancar(java.time.Duration.ofSeconds(2));

        DispositivoService.Ping ping = service.ping(1);

        assertThat(ping.totemOnline()).isTrue();
        assertThat(ping.segundosDesdeContato()).isEqualTo(2);
        assertThat(ping.reconhecimentoOnline()).isTrue();
        assertThat(ping.latenciaReconhecimentoMs()).isEqualTo(230);
        assertThat(telemetria.eventos(1).get(0).titulo()).isEqualTo("[PING] Catraca 01");
    }

    @Test
    void estacaoInexistenteDa404EComandosSemReleDao501() {
        assertThatThrownBy(() -> service.ping(99)).satisfies(e -> assertThat(status(e)).isEqualTo(HttpStatus.NOT_FOUND));
        assertThatThrownBy(() -> service.comandoNaoSuportado(1, "testar giro"))
                .satisfies(e -> assertThat(status(e)).isEqualTo(HttpStatus.NOT_IMPLEMENTED))
                .hasMessageContaining("relé");
    }

    @Test
    void sincronizarSemFaceApiConfiguradaDa503() {
        when(faceApi.isConfigurado()).thenReturn(false);
        assertThatThrownBy(() -> service.sincronizarFacial(1))
                .satisfies(e -> assertThat(status(e)).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
    }

    @Test
    void sincronizarDevolveOResumoDaFaceApi() {
        when(faceApi.isConfigurado()).thenReturn(true);
        when(faceApi.recarregarAgora()).thenReturn(Map.of("embeddings_em_cache", 42, "novos_ou_atualizados", 1));

        assertThat(service.sincronizarFacial(1)).containsEntry("embeddings_em_cache", 42);
        assertThat(telemetria.eventos(1).get(0).titulo()).isEqualTo("[SYNC] BASE DE ROSTOS ATUALIZADA");
    }

    @Test
    void syslogJuntaPassagensLiberacoesEEventosDoMaisRecente() {
        telemetria.registrarContato(1, null); // 15:00:00
        Pessoa maria = Pessoa.builder().cpf("47811936844").nome("Maria").build();
        when(logs.findByCatraca(anyLong(), anyInt())).thenReturn(List.of(
                new Log(5L, maria, TipoAcesso.ENTRADA, LocalDateTime.of(2026, 11, 27, 14, 59))));
        when(liberacoes.findRecentesDaCatraca(anyLong(), anyInt())).thenReturn(List.of(new Liberacao(
                3L, 1L, "Catraca 01", "Ana", "Visitante", Instant.parse("2026-11-27T14:58:00Z"),
                Instant.parse("2026-11-27T14:58:30Z"), Instant.parse("2026-11-27T14:58:02Z"), Liberacao.CONFIRMADA)));

        List<TelemetriaService.Evento> syslog = service.syslog(1);

        assertThat(syslog).extracting(TelemetriaService.Evento::titulo).containsExactly(
                "[HEARTBEAT] TOTEM CONECTADO", "[FACIAL_MATCH] ENTRADA", "[LIBERACAO_MANUAL] CONFIRMADA");
        assertThat(syslog.get(1).texto()).contains("Maria").contains("478.***.368-44");
        assertThat(syslog.get(2).texto()).contains("Ana").contains("Visitante");
    }
}
