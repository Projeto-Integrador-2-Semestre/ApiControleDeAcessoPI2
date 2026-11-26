package com.example.controledeacesso.repositories;

import com.example.controledeacesso.db.D1Client;
import com.example.controledeacesso.db.SqliteDateTime;
import com.example.controledeacesso.entity.Enum.TipoAcesso;
import com.example.controledeacesso.entity.Log;
import com.example.controledeacesso.entity.Pessoa;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class LogRepository {

    // A foto (base64) nao entra aqui: deixaria a listagem de logs pesada a toa.
    private static final String SELECT = """
            SELECT l.id, l.pessoa_cpf, l.tipo, l.data_hora, p.nome,
                   l.catraca_id, c.nome AS catraca_nome, c.local AS catraca_local
            FROM logs l
            LEFT JOIN pessoa p ON p.cpf = l.pessoa_cpf
            LEFT JOIN catraca c ON c.id = l.catraca_id
            """;

    private static final String ORDENACAO = " ORDER BY l.data_hora DESC, l.id DESC";

    private final D1Client d1;

    public LogRepository(D1Client d1) {
        this.d1 = d1;
    }

    public List<Log> findAll() {
        return mapear(d1.query(SELECT + ORDENACAO));
    }

    public Optional<Log> findById(Long id) {
        return mapear(d1.query(SELECT + " WHERE l.id = ?", id)).stream().findFirst();
    }

    public List<Log> findByPessoaCpf(String cpf) {
        return mapear(d1.query(SELECT + " WHERE l.pessoa_cpf = ?" + ORDENACAO, cpf));
    }

    public List<Log> findByPessoaCpfAndTipoAcesso(String cpf, TipoAcesso tipoAcesso) {
        return mapear(d1.query(SELECT + " WHERE l.pessoa_cpf = ? AND lower(l.tipo) = ?" + ORDENACAO,
                cpf, tipoAcesso.valorBanco()));
    }

    public List<Log> findByPessoaCpfAndDataHora(String cpf, LocalDateTime dataHora) {
        return mapear(d1.query(SELECT + " WHERE l.pessoa_cpf = ? AND datetime(l.data_hora) = datetime(?)" + ORDENACAO,
                cpf, SqliteDateTime.format(dataHora)));
    }

    /**
     * Busca com filtros opcionais (nulos sao ignorados). As datas sao em UTC.
     * datetime() normaliza os dois formatos gravados no banco: "2026-11-13T00:52:58.377Z"
     * (face_api) e "2026-11-13 00:52:58" (DEFAULT CURRENT_TIMESTAMP).
     */
    public List<Log> buscar(String cpf, TipoAcesso tipo, LocalDateTime de, LocalDateTime ate, int limite) {
        StringBuilder sql = new StringBuilder(SELECT).append(" WHERE 1=1");
        List<Object> params = new ArrayList<>();
        if (cpf != null && !cpf.isBlank()) {
            sql.append(" AND l.pessoa_cpf = ?");
            params.add(cpf);
        }
        if (tipo != null) {
            sql.append(" AND lower(l.tipo) = ?");
            params.add(tipo.valorBanco());
        }
        if (de != null) {
            sql.append(" AND datetime(l.data_hora) >= ?");
            params.add(SqliteDateTime.format(de));
        }
        if (ate != null) {
            sql.append(" AND datetime(l.data_hora) <= ?");
            params.add(SqliteDateTime.format(ate));
        }
        sql.append(ORDENACAO).append(" LIMIT ?");
        params.add(limite);
        return mapear(d1.query(sql.toString(), params.toArray()));
    }

    /** Insere um novo log. Se dataHora for nula, o banco usa CURRENT_TIMESTAMP. */
    public Log save(Log log) {
        List<Map<String, Object>> rows;
        if (log.getDataHora() == null) {
            rows = d1.query("""
                    INSERT INTO logs (pessoa_cpf, tipo) VALUES (?, ?)
                    RETURNING id, data_hora
                    """, log.getPessoa().getCpf(), log.getTipoAcesso().valorBanco());
        } else {
            rows = d1.query("""
                    INSERT INTO logs (pessoa_cpf, tipo, data_hora) VALUES (?, ?, ?)
                    RETURNING id, data_hora
                    """, log.getPessoa().getCpf(), log.getTipoAcesso().valorBanco(),
                    SqliteDateTime.format(log.getDataHora()));
        }
        if (!rows.isEmpty()) {
            Map<String, Object> row = rows.get(0);
            log.setId(((Number) row.get("id")).longValue());
            log.setDataHora(SqliteDateTime.parse(row.get("data_hora")));
        }
        return log;
    }

    public void deleteById(Long id) {
        d1.update("DELETE FROM logs WHERE id = ?", id);
    }

    private static List<Log> mapear(List<Map<String, Object>> rows) {
        return rows.stream().map(LogRepository::mapear).toList();
    }

    private static Log mapear(Map<String, Object> row) {
        Pessoa pessoa = Pessoa.builder()
                .cpf((String) row.get("pessoa_cpf"))
                .nome((String) row.get("nome"))
                .build();

        Object tipo = row.get("tipo");
        Object catracaId = row.get("catraca_id");
        return new Log(
                ((Number) row.get("id")).longValue(),
                pessoa,
                tipo == null ? null : TipoAcesso.deTexto(tipo.toString()),
                SqliteDateTime.parse(row.get("data_hora")),
                catracaId instanceof Number n ? n.longValue() : null,
                (String) row.get("catraca_nome"),
                (String) row.get("catraca_local")
        );
    }
}
