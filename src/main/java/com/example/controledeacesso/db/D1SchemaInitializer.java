package com.example.controledeacesso.db;

import com.example.controledeacesso.config.CloudflareD1Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Cria (se nao existirem) as tabelas que esta API controla e cadastra as catracas padrao
 * quando a tabela estiver vazia. As tabelas pessoa/logs seguem em db/schema.sql.
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

        Object total = d1.query("SELECT COUNT(*) AS total FROM catraca").get(0).get("total");
        if (total instanceof Number n && n.intValue() == 0) {
            CATRACAS_PADRAO.forEach(c -> d1.query("INSERT INTO catraca (nome, local) VALUES (?, ?)", c[0], c[1]));
            log.info("Cadastradas {} catracas padrão.", CATRACAS_PADRAO.size());
        }
    }
}
