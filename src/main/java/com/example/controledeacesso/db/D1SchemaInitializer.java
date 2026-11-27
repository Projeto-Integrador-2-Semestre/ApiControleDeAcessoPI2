package com.example.controledeacesso.db;

import com.example.controledeacesso.config.CloudflareD1Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Cria (se nao existirem) as tabelas que esta API controla e cadastra as catracas padrao
 * quando a tabela estiver vazia. As tabelas pessoa/logs seguem em db/schema.sql, mas as colunas
 * novas delas (status, motivo_bloqueio, data_cadastro, catraca_id) sao adicionadas aqui, sem
 * apagar nenhum dado (ver {@link #COLUNAS_NOVAS}).
 * Desative com cloudflare.d1.criar-tabelas=false.
 */
@Component
@Order(10)
public class D1SchemaInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(D1SchemaInitializer.class);

    static final List<String> DDL = List.of(
            """
            CREATE TABLE IF NOT EXISTS catraca (
                id    INTEGER PRIMARY KEY AUTOINCREMENT,
                nome  TEXT NOT NULL,
                local TEXT
            )""",
            """
            CREATE TABLE IF NOT EXISTS liberacao_catraca (
                id            INTEGER PRIMARY KEY AUTOINCREMENT,
                catraca_id    INTEGER NOT NULL,
                operador      TEXT,
                motivo        TEXT,
                solicitado_em TEXT NOT NULL,
                expira_em     TEXT,
                confirmado_em TEXT,
                status        TEXT NOT NULL DEFAULT 'pendente',
                FOREIGN KEY (catraca_id) REFERENCES catraca(id) ON DELETE CASCADE
            )""",
            "CREATE INDEX IF NOT EXISTS idx_liberacao_catraca ON liberacao_catraca (catraca_id)"
    );

    /**
     * Migracoes: {tabela, coluna, definicao}. O SQLite nao tem "ADD COLUMN IF NOT EXISTS", entao
     * cada coluna so e criada se ainda nao aparecer no PRAGMA table_info da tabela.
     * Nao da para usar DEFAULT CURRENT_TIMESTAMP num ALTER TABLE; a data_cadastro e preenchida pela API.
     */
    static final List<String[]> COLUNAS_NOVAS = List.of(
            new String[]{"pessoa", "status", "TEXT NOT NULL DEFAULT 'liberado'"},
            new String[]{"pessoa", "motivo_bloqueio", "TEXT"},
            new String[]{"pessoa", "data_cadastro", "TEXT"},
            // Catraca em que a face_api registrou a passagem (nulo nos logs antigos)
            new String[]{"logs", "catraca_id", "INTEGER"}
    );

    static final List<String[]> CATRACAS_PADRAO = List.of(
            new String[]{"Catraca 01", "Portaria Principal (Entrada A)"},
            new String[]{"Catraca 02", "Portaria Principal (Entrada B)"},
            new String[]{"Catraca 03", "Portão Docentes / Administrativo"},
            new String[]{"Catraca 04", "Acesso Oficinas / Metalmecânica"}
    );

    private final D1Client d1;
    private final CloudflareD1Properties props;

    public D1SchemaInitializer(D1Client d1, CloudflareD1Properties props) {
        this.d1 = d1;
        this.props = props;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!props.criarTabelasEfetivo()) {
            return;
        }
        DDL.forEach(d1::query);
        migrarColunas();

        Object total = d1.query("SELECT COUNT(*) AS total FROM catraca").get(0).get("total");
        if (total instanceof Number n && n.intValue() == 0) {
            CATRACAS_PADRAO.forEach(c -> d1.query("INSERT INTO catraca (nome, local) VALUES (?, ?)", c[0], c[1]));
            log.info("Cadastradas {} catracas padrão.", CATRACAS_PADRAO.size());
        }
    }

    void migrarColunas() {
        for (String[] coluna : COLUNAS_NOVAS) {
            Set<String> existentes = colunasDe(coluna[0]);
            if (existentes.isEmpty()) {
                // Tabela ainda nao existe (quem cria pessoa/logs e o db/schema.sql ou a face_api)
                continue;
            }
            if (!existentes.contains(coluna[1])) {
                d1.query("ALTER TABLE " + coluna[0] + " ADD COLUMN " + coluna[1] + " " + coluna[2]);
                log.info("Coluna {}.{} criada.", coluna[0], coluna[1]);
            }
        }
    }

    private Set<String> colunasDe(String tabela) {
        return d1.query("PRAGMA table_info(" + tabela + ")").stream()
                .map(linha -> String.valueOf(linha.get("name")))
                .collect(Collectors.toSet());
    }
}
