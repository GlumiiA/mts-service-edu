CREATE TABLE IF NOT EXISTS balances (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE,
    amount NUMERIC(10,2) NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_balances_user_id ON balances(user_id);

CREATE TABLE IF NOT EXISTS billing_transactions (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    application_id BIGINT NOT NULL,
    amount NUMERIC(10,2) NOT NULL,
    type VARCHAR(20) NOT NULL,
    description VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_billing_tx_user_id ON billing_transactions(user_id);
CREATE INDEX IF NOT EXISTS idx_billing_tx_app_id ON billing_transactions(application_id);

CREATE TABLE IF NOT EXISTS processed_messages (
    message_id VARCHAR(36) PRIMARY KEY,
    consumer VARCHAR(80) NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

INSERT INTO balances (user_id, amount) VALUES
    (1, 50000.00),
    (2, 99999.00),
    (3, 99999.00),
    (1000, 500.00)
ON CONFLICT (user_id) DO NOTHING;
