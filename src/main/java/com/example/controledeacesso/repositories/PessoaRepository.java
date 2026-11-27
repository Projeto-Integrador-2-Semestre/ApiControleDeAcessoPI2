package com.example.controledeacesso.repositories;

import com.example.controledeacesso.db.D1Client;
import com.example.controledeacesso.dto.PessoaResumoDTO;
import com.example.controledeacesso.entity.Pessoa;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class PessoaRepository {

    private static final String COLUNAS = "cpf, nome, foto, status, motivo_bloqueio, data_cadastro";

    // Mesma consulta da listagem, mas sem trazer a foto (base64) do banco
    private static final String SELECT_RESUMO = """
            SELECT cpf, nome, (foto IS NOT NULL AND foto <> '') AS possui_foto,
                   status, motivo_bloqueio, data_cadastro
            FROM pessoa
            """;

    private final D1Client d1;

    public PessoaRepository(D1Client d1) {
        this.d1 = d1;
    }

    public List<Pessoa> findAll() {
        return d1.query("SELECT " + COLUNAS + " FROM pessoa ORDER BY nome")
                .stream().map(PessoaRepository::mapear).toList();
    }

    /** Lista sem trazer a foto (base64) do banco, so indicando se ela existe. */
    public List<PessoaResumoDTO> findAllResumo() {
        return d1.query(SELECT_RESUMO + " ORDER BY nome").stream()
                .map(PessoaRepository::mapearResumo)
                .toList();
    }

    public Optional<PessoaResumoDTO> findResumoById(String cpf) {
        return d1.query(SELECT_RESUMO + " WHERE cpf = ?", cpf).stream()
                .findFirst().map(PessoaRepository::mapearResumo);
    }

    public Optional<Pessoa> findById(String cpf) {
        return d1.query("SELECT " + COLUNAS + " FROM pessoa WHERE cpf = ?", cpf)
                .stream().findFirst().map(PessoaRepository::mapear);
    }

    public boolean existsById(String cpf) {
        return !d1.query("SELECT 1 FROM pessoa WHERE cpf = ?", cpf).isEmpty();
    }

    /** Cadastro novo. Quem chama ja conferiu que o CPF nao existe (a PK tambem impede duplicado). */
    public Pessoa insert(Pessoa pessoa) {
        d1.update("""
                INSERT INTO pessoa (cpf, nome, foto, status, data_cadastro) VALUES (?, ?, ?, ?, ?)
                """, pessoa.getCpf(), pessoa.getNome(), pessoa.getFoto(), pessoa.getStatus(),
                texto(pessoa.getDataCadastro()));
        return pessoa;
    }

    /** Atualiza nome e foto. Devolve quantas linhas mudaram (0 = CPF nao existe). */
    public int atualizarDados(String cpf, String nome, String foto) {
        return d1.update("UPDATE pessoa SET nome = ?, foto = ? WHERE cpf = ?", nome, foto, cpf);
    }

    /** Bloqueia/libera. O motivo so faz sentido no bloqueio; ao liberar ele e apagado. */
    public int atualizarStatus(String cpf, String status, String motivoBloqueio) {
        return d1.update("UPDATE pessoa SET status = ?, motivo_bloqueio = ? WHERE cpf = ?",
                status, motivoBloqueio, cpf);
    }

    public int deleteById(String cpf) {
        return d1.update("DELETE FROM pessoa WHERE cpf = ?", cpf);
    }

    static Pessoa mapear(Map<String, Object> row) {
        return Pessoa.builder()
                .cpf((String) row.get("cpf"))
                .nome((String) row.get("nome"))
                .foto((String) row.get("foto"))
                .status(statusOuPadrao(row.get("status")))
                .motivoBloqueio((String) row.get("motivo_bloqueio"))
                .dataCadastro(instante(row.get("data_cadastro")))
                .build();
    }

    static PessoaResumoDTO mapearResumo(Map<String, Object> row) {
        return new PessoaResumoDTO(
                (String) row.get("cpf"),
                (String) row.get("nome"),
                row.get("possui_foto") instanceof Number n && n.intValue() != 0,
                statusOuPadrao(row.get("status")),
                (String) row.get("motivo_bloqueio"),
                instante(row.get("data_cadastro")));
    }

    private static String statusOuPadrao(Object valor) {
        return valor == null || valor.toString().isBlank() ? "liberado" : valor.toString();
    }

    private static String texto(Instant instante) {
        return instante == null ? null : instante.toString();
    }

    private static Instant instante(Object valor) {
        if (valor == null || valor.toString().isBlank()) {
            return null;
        }
        try {
            return Instant.parse(valor.toString());
        } catch (RuntimeException e) {
            // Valor fora do padrao ISO (ex.: editado a mao no painel do D1): melhor nao derrubar a listagem
            return null;
        }
    }
}
