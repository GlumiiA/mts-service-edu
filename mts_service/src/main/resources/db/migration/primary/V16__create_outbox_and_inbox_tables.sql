CREATE TABLE IF NOT EXISTS outbox_messages (
    id BIGSERIAL PRIMARY KEY,
    message_id VARCHAR(36) NOT NULL UNIQUE,
    destination VARCHAR(120) NOT NULL,
    event_type VARCHAR(120) NOT NULL,
    payload_type VARCHAR(255) NOT NULL,
    payload_json TEXT NOT NULL,
    status VARCHAR(20) NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    locked_at TIMESTAMPTZ,
    sent_at TIMESTAMPTZ,
    last_error TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

ALTER TABLE outbox_messages
    ADD COLUMN IF NOT EXISTS locked_at TIMESTAMPTZ;

ALTER TABLE outbox_messages
    ADD COLUMN IF NOT EXISTS sent_at TIMESTAMPTZ;

ALTER TABLE outbox_messages
    ADD COLUMN IF NOT EXISTS last_error TEXT;

CREATE INDEX IF NOT EXISTS idx_outbox_messages_status_next_attempt_at
    ON outbox_messages(status, next_attempt_at);

CREATE INDEX IF NOT EXISTS idx_outbox_messages_locked_at
    ON outbox_messages(locked_at);

CREATE TABLE IF NOT EXISTS message_inbox (
    id BIGSERIAL PRIMARY KEY,
    message_id VARCHAR(36) NOT NULL,
    consumer VARCHAR(80) NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_message_inbox_message_consumer UNIQUE (message_id, consumer)
);

CREATE INDEX IF NOT EXISTS idx_message_inbox_expires_at
    ON message_inbox(expires_at);
