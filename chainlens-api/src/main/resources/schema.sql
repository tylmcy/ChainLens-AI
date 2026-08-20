CREATE TABLE IF NOT EXISTS analyses (
    id            VARCHAR(36)  PRIMARY KEY,
    tx_hash       VARCHAR(66)  NOT NULL,
    chain_id      BIGINT       NOT NULL,
    network       VARCHAR(32)  NOT NULL,
    status        VARCHAR(16)  NOT NULL,
    facts         JSONB        NOT NULL,
    findings      JSONB        NOT NULL,
    report        JSONB        NOT NULL,
    tool_steps    JSONB        NOT NULL,
    rpc_payloads  JSONB        NOT NULL,
    metrics       JSONB        NOT NULL,
    llm_used      BOOLEAN      NOT NULL DEFAULT FALSE,
    degraded      BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at    TIMESTAMPTZ  NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_analyses_tx_hash ON analyses (tx_hash);
CREATE INDEX IF NOT EXISTS idx_analyses_created_at ON analyses (created_at);
