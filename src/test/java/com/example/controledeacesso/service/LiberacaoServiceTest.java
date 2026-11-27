package com.example.controledeacesso.service;

import com.example.controledeacesso.dispositivos.BloqueioGeralService;
import com.example.controledeacesso.entity.Catraca;
import com.example.controledeacesso.entity.Liberacao;
import com.example.controledeacesso.repositories.CatracaRepository;
import com.example.controledeacesso.repositories.LiberacaoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LiberacaoServiceTest {

    private CatracaRepository catracas;
    private LiberacaoRepository liberacoes;
    private RelogioAjustavel relogio;
    private LiberacaoService service;
    private BloqueioGeralService bloqueioGeral;

    @BeforeEach
    void setUp() {
        catracas = mock(CatracaRepository.class);
        liberacoes = mock(LiberacaoRepository.class);
        relogio = new RelogioAjustavel(Instant.parse("2026-11-24T12:00:00Z"));
        AtomicLong ids = new AtomicLong();
        when(catracas.findById(1L)).thenReturn(Optional.of(new Catraca(1L, "Catraca 01", "Portaria")));
        when(liberacoes.insert(any())).thenAnswer(inv -> ((Liberacao) inv.getArgument(0)).comId(ids.incrementAndGet()));
        bloqueioGeral = mock(BloqueioGeralService.class);
        service = new LiberacaoService(catracas, liberacoes, 30, Optional.of(relogio), bloqueioGeral);
    }

    @Test
    void tabletRecebeLiberacaoPendenteUmaUnicaVez() {
        Liberacao pedida = service.liberar(1L, "Operador", null);
        assertThat(pedida.status()).isEqualTo(Liberacao.PENDENTE);

        relogio.avancar(Duration.ofSeconds(2));
        Optional<Liberacao> primeira = service.confirmar(1L);
        assertThat(primeira).isPresent();
        assertThat(primeira.get().status()).isEqualTo(Liberacao.CONFIRMADA);
        assertThat(primeira.get().catracaNome()).isEqualTo("Catraca 01");
        verify(liberacoes).atualizarStatus(eq(pedida.id()), eq(Liberacao.CONFIRMADA), any());

        assertThat(service.confirmar(1L)).isEmpty();
        assertThat(service.buscar(pedida.id())).get().extracting(Liberacao::status).isEqualTo(Liberacao.CONFIRMADA);
    }

    @Test
    void liberacaoNaoBuscadaNoPrazoExpira() {
        Liberacao pedida = service.liberar(1L, null, null);

        relogio.avancar(Duration.ofSeconds(31));
        assertThat(service.buscar(pedida.id())).get().extracting(Liberacao::status).isEqualTo(Liberacao.EXPIRADA);
        assertThat(service.confirmar(1L)).isEmpty();
        verify(liberacoes).atualizarStatus(eq(pedida.id()), eq(Liberacao.EXPIRADA), isNull());
    }

    @Test
    void novoPedidoSubstituiOAnterior() {
        Liberacao primeira = service.liberar(1L, null, null);
        Liberacao segunda = service.liberar(1L, null, null);

        verify(liberacoes).atualizarStatus(eq(primeira.id()), eq(Liberacao.EXPIRADA), isNull());
        assertThat(service.confirmar(1L)).get().extracting(Liberacao::id).isEqualTo(segunda.id());
    }

    @Test
    void catracaInexistenteDa404() {
        when(catracas.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.liberar(99L, null, null)).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void bloqueioGeralImpedeLiberacaoManual() {
        when(bloqueioGeral.ativo()).thenReturn(true);
        assertThatThrownBy(() -> service.liberar(1L, "Operador", null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Bloqueio geral ativo");
    }

    @Test
    void catracaSemPedidoNaoLibera() {
        assertThat(service.confirmar(1L)).isEmpty();
    }

    /** Clock que o teste consegue avancar. */
    static class RelogioAjustavel extends Clock {
        private Instant agora;

        RelogioAjustavel(Instant inicio) {
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
            return this;
        }

        @Override
        public Instant instant() {
            return agora;
        }
    }
}
