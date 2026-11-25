package com.example.controledeacesso.service;

import com.example.controledeacesso.dto.PessoaDTO;
import com.example.controledeacesso.dto.PessoaResumoDTO;
import com.example.controledeacesso.entity.Pessoa;
import com.example.controledeacesso.repositories.PessoaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.Base64;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PessoaService {

    private final PessoaRepository pessoaRepository;

    public void cadastroPessoa(PessoaDTO data) throws IOException {
        String cpf = normalizarCpf(data.getCpf());
        if (data.getNome() == null || data.getNome().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe o nome.");
        }
        if (data.getFoto() == null || data.getFoto().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Envie a foto do rosto.");
        }
        // O save faz upsert: sem esta checagem um cadastro novo sobrescreveria outra pessoa.
        if (pessoaRepository.existsById(cpf)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Já existe uma pessoa cadastrada com este CPF.");
        }

        String fotoBase64 = Base64.getEncoder()
                .encodeToString(data.getFoto().getBytes());

        Pessoa pessoa = Pessoa.builder()
                .cpf(cpf)
                .nome(data.getNome().trim())
                .foto(fotoBase64)
                .build();

        pessoaRepository.save(pessoa);
    }

    public Pessoa buscarPessoaPorCpf(String cpf) {
        return pessoaRepository.findById(cpf).orElse(null);
    }

    public void atualizarPessoa(PessoaDTO data) throws IOException {
        Pessoa pessoa = pessoaRepository.findById(data.getCpf()).orElse(null);
        if (pessoa == null) {
            return;
        }

        pessoa.setNome(data.getNome());

        if (data.getFoto() != null && !data.getFoto().isEmpty()) {
            String fotoBase64 = Base64.getEncoder()
                    .encodeToString(data.getFoto().getBytes());
            pessoa.setFoto(fotoBase64);
        }

        pessoaRepository.save(pessoa);
    }

    public void deletarPessoa(String cpf) {
        pessoaRepository.deleteById(cpf);
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
}
