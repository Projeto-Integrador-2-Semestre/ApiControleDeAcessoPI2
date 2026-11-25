package com.example.controledeacesso.repositories;

import com.example.controledeacesso.db.D1Client;
import com.example.controledeacesso.dto.PessoaResumoDTO;
import com.example.controledeacesso.entity.Pessoa;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class PessoaRepository {

    private static final String COLUNAS = "cpf, nome, foto";

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
        return d1.query("""
                SELECT cpf, nome, (foto IS NOT NULL AND foto <> '') AS possui_foto
                FROM pessoa ORDER BY nome
                """).stream()
                .map(row -> new PessoaResumoDTO(
                        (String) row.get("cpf"),
                        (String) row.get("nome"),
                        row.get("possui_foto") instanceof Number n && n.intValue() != 0))
                .toList();
    }

    public Optional<Pessoa> findById(String cpf) {
        return d1.query("SELECT " + COLUNAS + " FROM pessoa WHERE cpf = ?", cpf)
                .stream().findFirst().map(PessoaRepository::mapear);
    }

    public boolean existsById(String cpf) {
        return !d1.query("SELECT 1 FROM pessoa WHERE cpf = ?", cpf).isEmpty();
    }

    /** Insere ou atualiza (upsert) pela chave primaria cpf. */
    public Pessoa save(Pessoa pessoa) {
        d1.update("""
                INSERT INTO pessoa (cpf, nome, foto) VALUES (?, ?, ?)
                ON CONFLICT(cpf) DO UPDATE SET nome = excluded.nome, foto = excluded.foto
                """, pessoa.getCpf(), pessoa.getNome(), pessoa.getFoto());
        return pessoa;
    }

    public void deleteById(String cpf) {
        d1.update("DELETE FROM pessoa WHERE cpf = ?", cpf);
    }

    static Pessoa mapear(Map<String, Object> row) {
        return Pessoa.builder()
                .cpf((String) row.get("cpf"))
                .nome((String) row.get("nome"))
                .foto((String) row.get("foto"))
                .build();
    }
}
