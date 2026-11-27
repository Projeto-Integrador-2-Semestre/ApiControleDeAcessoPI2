-- Schema do banco Cloudflare D1 "projetointegrador" (SQLite)
-- Em bancos ja existentes, a API adiciona as colunas novas sozinha na subida (D1SchemaInitializer).
CREATE TABLE IF NOT EXISTS pessoa (
    cpf             TEXT PRIMARY KEY,
    nome            TEXT NOT NULL,
    foto            TEXT,
    status          TEXT NOT NULL DEFAULT 'liberado', -- 'liberado' | 'bloqueado'
    motivo_bloqueio TEXT,
    data_cadastro   TEXT                              -- ISO 8601 UTC, preenchida pela API
);

CREATE TABLE IF NOT EXISTS logs (
    id         INTEGER PRIMARY KEY AUTOINCREMENT,
    pessoa_cpf TEXT NOT NULL,
    tipo       TEXT NOT NULL,
    data_hora  DATETIME DEFAULT CURRENT_TIMESTAMP,
    catraca_id INTEGER, -- catraca do totem que registrou (gravada pela face_api)
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
