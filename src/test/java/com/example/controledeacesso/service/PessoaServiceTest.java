package com.example.controledeacesso.service;

import com.example.controledeacesso.dto.PessoaDTO;
import com.example.controledeacesso.dto.PessoaResumoDTO;
import com.example.controledeacesso.entity.Pessoa;
import com.example.controledeacesso.integracao.FaceApiNotifier;
import com.example.controledeacesso.repositories.PessoaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PessoaServiceTest {

    private static final String CPF = "47811936844";
    private static final Instant AGORA = Instant.parse("2026-11-26T15:30:00Z");

    private PessoaRepository repo;
    private FaceApiNotifier faceApi;
    private PessoaService service;

    @BeforeEach
    void setUp() {
        repo = mock(PessoaRepository.class);
        faceApi = mock(FaceApiNotifier.class);
        service = new PessoaService(repo, faceApi, Optional.of(Clock.fixed(AGORA, ZoneOffset.UTC)));
    }

    private static PessoaDTO dto(String cpf, String nome, boolean comFoto) {
        MockMultipartFile foto = comFoto ? new MockMultipartFile("foto", "rosto.jpg", "image/jpeg", new byte[]{1, 2, 3}) : null;
        return new PessoaDTO(cpf, nome, foto);
    }

    private static HttpStatus status(Throwable e) {
        return HttpStatus.valueOf(((ResponseStatusException) e).getStatusCode().value());
    }

    // ----- cadastro -----

    @Test
    void cadastroNormalizaCpfGravaDataEAvisaFaceApi() throws Exception {
        service.cadastroPessoa(dto("478.119.368-44", "  Maria  ", true));

        ArgumentCaptor<Pessoa> gravada = ArgumentCaptor.forClass(Pessoa.class);
        verify(repo).insert(gravada.capture());
        assertThat(gravada.getValue().getCpf()).isEqualTo(CPF);
        assertThat(gravada.getValue().getNome()).isEqualTo("Maria");
        assertThat(gravada.getValue().getStatus()).isEqualTo("liberado");
        assertThat(gravada.getValue().getDataCadastro()).isEqualTo(AGORA);
        verify(faceApi).pessoasAlteradas();
    }

    @Test
    void cadastroComCpfDuplicadoDa409() {
        when(repo.existsById(CPF)).thenReturn(true);

        assertThatThrownBy(() -> service.cadastroPessoa(dto(CPF, "Maria", true)))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(HttpStatus.CONFLICT))
                .hasMessageContaining("Já existe uma pessoa cadastrada com este CPF");
        verify(repo, never()).insert(any());
    }

    @Test
    void cadastroSemFotoOuComCpfCurtoDa400() {
        assertThatThrownBy(() -> service.cadastroPessoa(dto(CPF, "Maria", false)))
                .satisfies(e -> assertThat(status(e)).isEqualTo(HttpStatus.BAD_REQUEST));
        assertThatThrownBy(() -> service.cadastroPessoa(dto("123", "Maria", true)))
                .satisfies(e -> assertThat(status(e)).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    // ----- atualizacao (B1) -----

    @Test
    void atualizacaoDeCpfInexistenteDa404() {
        when(repo.findById(CPF)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.atualizarPessoa(dto(CPF, "Maria", false)))
                .satisfies(e -> assertThat(status(e)).isEqualTo(HttpStatus.NOT_FOUND));
        verify(repo, never()).atualizarDados(anyString(), anyString(), any());
    }

    @Test
    void atualizacaoAceitaCpfFormatadoEMantemAFotoAtual() throws Exception {
        when(repo.findById(CPF)).thenReturn(Optional.of(Pessoa.builder().cpf(CPF).nome("Antigo").foto("base64antigo").build()));
        when(repo.atualizarDados(CPF, "Novo Nome", "base64antigo")).thenReturn(1);

        service.atualizarPessoa(dto("478.119.368-44", " Novo Nome ", false));

        verify(repo).atualizarDados(CPF, "Novo Nome", "base64antigo");
        verify(faceApi).pessoasAlteradas();
    }

    @Test
    void atualizacaoSemNomeDa400() {
        assertThatThrownBy(() -> service.atualizarPessoa(dto(CPF, "   ", false)))
                .satisfies(e -> assertThat(status(e)).isEqualTo(HttpStatus.BAD_REQUEST));
        assertThatThrownBy(() -> service.atualizarPessoa(dto(CPF, null, false)))
                .satisfies(e -> assertThat(status(e)).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    // ----- bloqueio (B2) -----

    @Test
    void bloqueioGravaMotivoEAvisaFaceApi() {
        when(repo.atualizarStatus(CPF, "bloqueado", "Matrícula trancada")).thenReturn(1);
        PessoaResumoDTO resumo = new PessoaResumoDTO(CPF, "Maria", true, "bloqueado", "Matrícula trancada", null);
        when(repo.findResumoById(CPF)).thenReturn(Optional.of(resumo));

        assertThat(service.alterarStatus(CPF, "BLOQUEADO", " Matrícula trancada ")).isEqualTo(resumo);
        verify(faceApi).pessoasAlteradas();
    }

    @Test
    void bloqueioSemMotivoUsaOPadraoELiberacaoApagaOMotivo() {
        when(repo.atualizarStatus(eq(CPF), anyString(), any())).thenReturn(1);
        when(repo.findResumoById(CPF)).thenReturn(Optional.of(new PessoaResumoDTO(CPF, "Maria", true, "liberado", null, null)));

        service.alterarStatus(CPF, "bloqueado", null);
        verify(repo).atualizarStatus(CPF, "bloqueado", "Bloqueio administrativo");

        service.alterarStatus(CPF, "liberado", "ignorado");
        verify(repo).atualizarStatus(eq(CPF), eq("liberado"), isNull());
    }

    @Test
    void statusInvalidoDa400EPessoaInexistenteDa404() {
        assertThatThrownBy(() -> service.alterarStatus(CPF, "suspenso", null))
                .satisfies(e -> assertThat(status(e)).isEqualTo(HttpStatus.BAD_REQUEST));
        assertThatThrownBy(() -> service.alterarStatus(CPF, null, null))
                .satisfies(e -> assertThat(status(e)).isEqualTo(HttpStatus.BAD_REQUEST));

        when(repo.atualizarStatus(eq(CPF), anyString(), any())).thenReturn(0);
        assertThatThrownBy(() -> service.alterarStatus(CPF, "bloqueado", "x"))
                .satisfies(e -> assertThat(status(e)).isEqualTo(HttpStatus.NOT_FOUND));
        verify(faceApi, never()).pessoasAlteradas();
    }

    // ----- exclusao -----

    @Test
    void exclusaoDeCpfInexistenteDa404() {
        when(repo.deleteById(CPF)).thenReturn(0);
        assertThatThrownBy(() -> service.deletarPessoa("478.119.368-44"))
                .satisfies(e -> assertThat(status(e)).isEqualTo(HttpStatus.NOT_FOUND));
    }
}
