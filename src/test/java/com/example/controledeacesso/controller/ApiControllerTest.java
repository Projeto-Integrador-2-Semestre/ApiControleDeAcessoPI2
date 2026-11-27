package com.example.controledeacesso.controller;

import com.example.controledeacesso.dto.PessoaResumoDTO;
import com.example.controledeacesso.entity.Catraca;
import com.example.controledeacesso.repositories.CatracaRepository;
import com.example.controledeacesso.repositories.PessoaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Testa a API pela camada HTTP (seguranca + controllers + formato de erro), com o banco mockado.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ApiControllerTest {

    private static final String CPF = "47811936844";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private PessoaRepository pessoas;

    @MockitoBean
    private CatracaRepository catracas;

    private String token;

    @BeforeEach
    void login() throws Exception {
        MvcResult resultado = mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuario\":\"admin\",\"senha\":\"1234\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tipo").value("Bearer"))
                .andExpect(jsonPath("$.nome").value("Administrador"))
                .andReturn();
        token = "Bearer " + JsonMapper.builder().build()
                .readTree(resultado.getResponse().getContentAsString()).get("token").asString();
    }

    @Test
    void semTokenDa401NoFormatoDoFront() throws Exception {
        mvc.perform(get("/pessoa/listar"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Faça login para continuar."));
        mvc.perform(get("/logs").header("Authorization", "Bearer token-invalido"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void loginErradoDa401() throws Exception {
        mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuario\":\"admin\",\"senha\":\"errada\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Usuário ou senha inválidos."));
    }

    @Test
    void healthETabletNaoPrecisamDeLogin() throws Exception {
        mvc.perform(get("/health")).andExpect(status().isOk());
        // Sem liberacao pendente o tablet recebe 204
        mvc.perform(post("/catracas/1/liberacao/confirmar")).andExpect(status().isNoContent());
    }

    @Test
    void listagemTrazStatusEDataDeCadastro() throws Exception {
        when(pessoas.findAllResumo()).thenReturn(List.of(new PessoaResumoDTO(
                CPF, "Maria", true, "bloqueado", "Matrícula trancada", Instant.parse("2026-11-26T15:30:00Z"))));

        mvc.perform(get("/pessoa/listar").param("foto", "false").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("bloqueado"))
                .andExpect(jsonPath("$[0].motivoBloqueio").value("Matrícula trancada"))
                .andExpect(jsonPath("$[0].dataCadastro").value("2026-11-26T15:30:00Z"));
    }

    @Test
    void cadastroDuplicadoDa409ComDetail() throws Exception {
        when(pessoas.existsById(CPF)).thenReturn(true);

        mvc.perform(multipart("/pessoa/cadastro")
                        .file(new MockMultipartFile("foto", "rosto.jpg", "image/jpeg", new byte[]{1, 2, 3}))
                        .param("cpf", CPF).param("nome", "Maria")
                        .header("Authorization", token))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.detail").value("Já existe uma pessoa cadastrada com este CPF."));
    }

    @Test
    void atualizacaoDeCpfInexistenteDa404() throws Exception {
        when(pessoas.findById(CPF)).thenReturn(Optional.empty());

        mvc.perform(multipart("/pessoa/atualizar")
                        .param("cpf", "478.119.368-44").param("nome", "Maria")
                        .with(r -> { r.setMethod("PUT"); return r; })
                        .header("Authorization", token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Pessoa não encontrada."));
    }

    @Test
    void bloqueioPeloPatch() throws Exception {
        when(pessoas.atualizarStatus(eq(CPF), eq("bloqueado"), anyString())).thenReturn(1);
        when(pessoas.findResumoById(CPF)).thenReturn(Optional.of(
                new PessoaResumoDTO(CPF, "Maria", true, "bloqueado", "Teste", null)));

        mvc.perform(patch("/pessoa/{cpf}/status", CPF)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"bloqueado\",\"motivo\":\"Teste\"}")
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("bloqueado"));

        mvc.perform(patch("/pessoa/{cpf}/status", CPF)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"talvez\"}")
                        .header("Authorization", token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("status deve ser 'liberado' ou 'bloqueado'."));
    }

    @Test
    void errosDoProprioSpringTambemVemComDetail() throws Exception {
        // JSON malformado
        mvc.perform(patch("/pessoa/{cpf}/status", CPF)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{nao e json")
                        .header("Authorization", token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").exists());
        // Parametro com tipo errado
        mvc.perform(get("/logs").param("limite", "abc").header("Authorization", token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").exists());
    }

    @Test
    void dispositivosExigemLoginEComandosSemReleDao501() throws Exception {
        mvc.perform(get("/dispositivos/estacoes")).andExpect(status().isUnauthorized());
        when(catracas.findById(1L)).thenReturn(Optional.of(new Catraca(1L, "Catraca 01", "Portaria")));
        mvc.perform(post("/dispositivos/estacoes/{id}/testar-giro", 1).header("Authorization", token))
                .andExpect(status().isNotImplemented())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("relé")));
        mvc.perform(post("/dispositivos/estacoes/{id}/ping", 99).header("Authorization", token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Estação não encontrada."));
    }
}
