CREATE TABLE IF NOT EXISTS outbox_messages (
    id VARCHAR(36) PRIMARY KEY,
    event_type VARCHAR(120) NOT NULL,
    destination VARCHAR(255) NOT NULL,
    payload TEXT NOT NULL,
    status VARCHAR(16) NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL,
    sent_at TIMESTAMPTZ,
    last_error TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_outbox_dispatch
    ON outbox_messages (status, next_attempt_at, created_at);

CREATE TABLE IF NOT EXISTS processed_messages (
    message_id VARCHAR(36) PRIMARY KEY,
    consumer VARCHAR(80) NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

