package com.example.controledeacesso.service;

import com.example.controledeacesso.dto.PessoaDTO;
import com.example.controledeacesso.dto.PessoaResumoDTO;
import com.example.controledeacesso.entity.Enum.StatusPessoa;
import com.example.controledeacesso.entity.Pessoa;
import com.example.controledeacesso.integracao.FaceApiNotifier;
import com.example.controledeacesso.repositories.PessoaRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

@Service
public class PessoaService {

    static final int TAMANHO_MAXIMO_MOTIVO = 200;

    private final PessoaRepository pessoaRepository;
    private final FaceApiNotifier faceApi;
    private final Clock clock;

    public PessoaService(PessoaRepository pessoaRepository, FaceApiNotifier faceApi, Optional<Clock> clock) {
        this.pessoaRepository = pessoaRepository;
        this.faceApi = faceApi;
        this.clock = clock.orElse(Clock.systemUTC());
    }

    public void cadastroPessoa(PessoaDTO data) throws IOException {
        String cpf = normalizarCpf(data.getCpf());
        String nome = validarNome(data.getNome());
        if (data.getFoto() == null || data.getFoto().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Envie a foto do rosto.");
        }
        if (pessoaRepository.existsById(cpf)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Já existe uma pessoa cadastrada com este CPF.");
        }

        String fotoBase64 = Base64.getEncoder()
                .encodeToString(data.getFoto().getBytes());

        Pessoa pessoa = Pessoa.builder()
                .cpf(cpf)
                .nome(nome)
                .foto(fotoBase64)
                .status(StatusPessoa.LIBERADO.valorBanco())
                .dataCadastro(clock.instant().truncatedTo(ChronoUnit.SECONDS))
                .build();

        pessoaRepository.insert(pessoa);
        faceApi.pessoasAlteradas();
    }

    /** Aceita o CPF com ou sem pontuacao. Devolve null se nao existir. */
    public Pessoa buscarPessoaPorCpf(String cpf) {
        return pessoaRepository.findById(normalizarCpf(cpf)).orElse(null);
    }

    /** Atualiza nome e, se vier, a foto. 404 quando o CPF nao existe. */
    public void atualizarPessoa(PessoaDTO data) throws IOException {
        String cpf = normalizarCpf(data.getCpf());
        String nome = validarNome(data.getNome());
        Pessoa pessoa = pessoaRepository.findById(cpf).orElseThrow(PessoaService::naoEncontrada);

        String foto = pessoa.getFoto();
        if (data.getFoto() != null && !data.getFoto().isEmpty()) {
            foto = Base64.getEncoder().encodeToString(data.getFoto().getBytes());
        }

        if (pessoaRepository.atualizarDados(cpf, nome, foto) == 0) {
            // Alguem excluiu a pessoa entre a busca e o update
            throw naoEncontrada();
        }
        faceApi.pessoasAlteradas();
    }

    /**
     * Bloqueia ou libera o acesso. O motivo e obrigatorio no bloqueio (aparece no painel) e
     * apagado na liberacao. A face_api passa a recusar a pessoa com motivo "pessoa_bloqueada".
     */
    public PessoaResumoDTO alterarStatus(String cpfInformado, String statusInformado, String motivo) {
        String cpf = normalizarCpf(cpfInformado);
        StatusPessoa status;
        try {
            if (statusInformado == null || statusInformado.isBlank()) {
                throw new IllegalArgumentException();
            }
            status = StatusPessoa.deTexto(statusInformado);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "status deve ser 'liberado' ou 'bloqueado'.");
        }

        String motivoFinal = null;
        if (status == StatusPessoa.BLOQUEADO) {
            motivoFinal = motivo == null || motivo.isBlank() ? "Bloqueio administrativo" : motivo.trim();
            if (motivoFinal.length() > TAMANHO_MAXIMO_MOTIVO) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "O motivo do bloqueio deve ter no máximo " + TAMANHO_MAXIMO_MOTIVO + " caracteres.");
            }
        }

        if (pessoaRepository.atualizarStatus(cpf, status.valorBanco(), motivoFinal) == 0) {
            throw naoEncontrada();
        }
        faceApi.pessoasAlteradas();
        return pessoaRepository.findResumoById(cpf).orElseThrow(PessoaService::naoEncontrada);
    }

    public void deletarPessoa(String cpf) {
        if (pessoaRepository.deleteById(normalizarCpf(cpf)) == 0) {
            throw naoEncontrada();
        }
        faceApi.pessoasAlteradas();
    }

    public List<Pessoa> listarPessoas() {
        return pessoaRepository.findAll();
    }

    public List<PessoaResumoDTO> listarPessoasResumo() {
        return pessoaRepository.findAllResumo();
    }

    /** O banco guarda o CPF so com digitos (a face_api usa esse valor nos logs). */
    static String normalizarCpf(String cpf) {
        String digitos = cpf == null ? "" : cpf.replaceAll("\\D", "");
        if (digitos.length() != 11) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "CPF deve ter 11 dígitos.");
        }
        return digitos;
    }

    static String validarNome(String nome) {
        if (nome == null || nome.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe o nome.");
        }
        return nome.trim();
    }

    private static ResponseStatusException naoEncontrada() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Pessoa não encontrada.");
    }
}
