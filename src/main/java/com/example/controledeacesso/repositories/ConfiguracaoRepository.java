package com.example.controledeacesso.repositories;

import com.example.controledeacesso.db.D1Client;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * Tabela "configuracao" (chave, valor, alterado_em, alterado_por): ajustes do sistema que
 * precisam valer para todos os servicos. A face_api le a chave "bloqueio_geral" daqui.
 */
@Repository
public class ConfiguracaoRepository {

    public record Configuracao(String chave, String valor, Instant alteradoEm, String alteradoPor) {
    }

    private final D1Client d1;

    public ConfiguracaoRepository(D1Client d1) {
        this.d1 = d1;
    }

    public Optional<Configuracao> buscar(String chave) {
        return d1.query("SELECT chave, valor, alterado_em, alterado_por FROM configuracao WHERE chave = ?", chave)
                .stream().findFirst().map(ConfiguracaoRepository::mapear);
    }

    public void salvar(String chave, String valor, Instant alteradoEm, String alteradoPor) {
        d1.update("""
                INSERT INTO configuracao (chave, valor, alterado_em, alterado_por) VALUES (?, ?, ?, ?)
                ON CONFLICT(chave) DO UPDATE SET valor = excluded.valor,
                    alterado_em = excluded.alterado_em, alterado_por = excluded.alterado_por
                """, chave, valor, alteradoEm.toString(), alteradoPor);
    }

    private static Configuracao mapear(Map<String, Object> row) {
        Object em = row.get("alterado_em");
        return new Configuracao(
                (String) row.get("chave"),
                (String) row.get("valor"),
                em == null ? null : Instant.parse(em.toString()),
                (String) row.get("alterado_por"));
    }
}
