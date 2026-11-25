package com.example.controledeacesso.repositories;

import com.example.controledeacesso.db.D1Client;
import com.example.controledeacesso.entity.Liberacao;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class LiberacaoRepository {

    private static final String SELECT = """
            SELECT lc.id, lc.catraca_id, c.nome AS catraca_nome, lc.operador, lc.motivo,
                   lc.solicitado_em, lc.expira_em, lc.confirmado_em, lc.status
            FROM liberacao_catraca lc
            LEFT JOIN catraca c ON c.id = lc.catraca_id
            """;

    private final D1Client d1;

    public LiberacaoRepository(D1Client d1) {
        this.d1 = d1;
    }

    public Liberacao insert(Liberacao l) {
        Map<String, Object> row = d1.query("""
                INSERT INTO liberacao_catraca (catraca_id, operador, motivo, solicitado_em, expira_em, status)
                VALUES (?, ?, ?, ?, ?, ?) RETURNING id
                """, l.catracaId(), l.operador(), l.motivo(), texto(l.solicitadoEm()), texto(l.expiraEm()), l.status())
                .get(0);
        return l.comId(((Number) row.get("id")).longValue());
    }

    public void atualizarStatus(Long id, String status, Instant confirmadoEm) {
        d1.update("UPDATE liberacao_catraca SET status = ?, confirmado_em = ? WHERE id = ?",
                status, texto(confirmadoEm), id);
    }

    public Optional<Liberacao> findById(Long id) {
        return d1.query(SELECT + " WHERE lc.id = ?", id).stream().findFirst().map(LiberacaoRepository::mapear);
    }

    public List<Liberacao> findRecentes(int limite) {
        return d1.query(SELECT + " ORDER BY lc.id DESC LIMIT ?", limite).stream()
                .map(LiberacaoRepository::mapear).toList();
    }

    private static String texto(Instant instante) {
        return instante == null ? null : instante.toString();
    }

    private static Instant instante(Object valor) {
        return valor == null ? null : Instant.parse(valor.toString());
    }

    private static Liberacao mapear(Map<String, Object> row) {
        return new Liberacao(
                ((Number) row.get("id")).longValue(),
                ((Number) row.get("catraca_id")).longValue(),
                (String) row.get("catraca_nome"),
                (String) row.get("operador"),
                (String) row.get("motivo"),
                instante(row.get("solicitado_em")),
                instante(row.get("expira_em")),
                instante(row.get("confirmado_em")),
                (String) row.get("status"));
    }
}
