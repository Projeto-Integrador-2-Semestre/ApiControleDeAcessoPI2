package com.example.controledeacesso.controller;

import com.example.controledeacesso.dto.PessoaDTO;
import com.example.controledeacesso.entity.Pessoa;
import com.example.controledeacesso.service.PessoaService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/pessoa")
@RequiredArgsConstructor
public class PessoaController {

    private final PessoaService pessoaService;

    /**
     * Lista as pessoas. Com ?foto=false a foto (base64) nao e enviada, deixando a resposta
     * bem mais leve para telas de listagem; nesse caso vem o campo "possuiFoto".
     */
    @GetMapping("/listar")
    public List<?> listarPessoas(@RequestParam(defaultValue = "true") boolean foto) {
        return foto ? pessoaService.listarPessoas() : pessoaService.listarPessoasResumo();
    }

    @GetMapping("/{cpf}")
    public ResponseEntity<Pessoa> buscarPessoa(@PathVariable String cpf) {
        Pessoa pessoa = pessoaService.buscarPessoaPorCpf(cpf);
        return pessoa != null ? ResponseEntity.ok(pessoa) : ResponseEntity.notFound().build();
    }

    /** Cadastro (multipart/form-data: cpf, nome, foto). Responde 409 se o CPF ja existir. */
    @PostMapping("/cadastro")
    public ResponseEntity<String> cadastroPessoa(
            @ModelAttribute PessoaDTO data
    ) throws IOException {

        pessoaService.cadastroPessoa(data);

        return ResponseEntity.ok(
                "Acesso de " + data.getNome() + " registrado!"
        );
    }

    @PutMapping("/atualizar")
    public ResponseEntity<String> atualizarPessoa(@ModelAttribute PessoaDTO data) throws IOException {
        pessoaService.atualizarPessoa(data);
        return ResponseEntity.ok("Informações de Acesso atualizado com sucesso!");
    }

    @DeleteMapping("/{cpf}")
    public ResponseEntity<String> deletarPessoa(@PathVariable String cpf) {
        pessoaService.deletarPessoa(cpf);
        return ResponseEntity.ok("Acesso deletado com sucesso!");
    }
}
