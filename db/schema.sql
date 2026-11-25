-- Schema do banco Cloudflare D1 "projetointegrador" (SQLite)
CREATE TABLE IF NOT EXISTS pessoa (
    cpf  TEXT PRIMARY KEY,
    nome TEXT NOT NULL,
    foto TEXT
);

CREATE TABLE IF NOT EXISTS logs (
    id         INTEGER PRIMARY KEY AUTOINCREMENT,
    pessoa_cpf TEXT NOT NULL,
    tipo       TEXT NOT NULL,
    data_hora  DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (pessoa_cpf) REFERENCES pessoa(cpf) ON DELETE CASCADE
);

-- Catracas e liberacoes manuais (criadas automaticamente pela API na subida)
CREATE TABLE IF NOT EXISTS catraca (
    id    INTEGER PRIMARY KEY AUTOINCREMENT,
    nome  TEXT NOT NULL,
    local TEXT
);

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
);
