package com.example.controledeacesso.dispositivos;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class TelemetriaServiceTest {

    private final RelogioTeste relogio = new RelogioTeste(Instant.parse("2026-11-27T12:00:00Z"));
    private final TelemetriaService telemetria = new TelemetriaService(Optional.of(relogio));

    @Test
    void totemQueNuncaConsultouFicaOfflineSemContato() {
        assertThat(telemetria.online(1)).isFalse();
        assertThat(telemetria.segundosDesdeContato(1)).isNull();
    }

    @Test
    void consultaDoTabletDeixaOnlineAteOLimite() {
        telemetria.registrarContato(1, "200.1.2.3");
        assertThat(telemetria.online(1)).isTrue();
        assertThat(telemetria.ultimoContato(1)).get().extracting(TelemetriaService.Contato::ip).isEqualTo("200.1.2.3");

        relogio.avancar(Duration.ofSeconds(10));
        assertThat(telemetria.online(1)).isTrue();
        relogio.avancar(Duration.ofSeconds(1));
        assertThat(telemetria.online(1)).isFalse();
        assertThat(telemetria.segundosDesdeContato(1)).isEqualTo(11);
    }

    @Test
    void registraConexaoEReconexaoNoSyslog() {
        telemetria.registrarContato(1, null);
        relogio.avancar(Duration.ofSeconds(2));
        telemetria.registrarContato(1, null); // consulta normal: nao gera evento
        relogio.avancar(Duration.ofSeconds(30));
        telemetria.registrarContato(1, null);

        assertThat(telemetria.eventos(1)).extracting(TelemetriaService.Evento::titulo)
                .containsExactly("[HEARTBEAT] TOTEM RECONECTADO", "[HEARTBEAT] TOTEM CONECTADO");
        assertThat(telemetria.eventos(1).get(0).texto()).contains("30 s");
    }

    /** Clock que o teste consegue avancar. */
    static class RelogioTeste extends Clock {
        private Instant agora;

        RelogioTeste(Instant inicio) {
            this.agora = inicio;
        }

        void avancar(Duration d) {
            agora = agora.plus(d);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return Clock.fixed(agora, zone);
        }

        @Override
        public Instant instant() {
            return agora;
        }
    }
}
