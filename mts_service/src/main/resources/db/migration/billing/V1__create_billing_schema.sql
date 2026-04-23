-- V1: billing schema - balances and billing transactions
CREATE TABLE balances (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE,
    amount NUMERIC(10,2) NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_balances_user_id ON balances(user_id);

CREATE TABLE billing_transactions (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    application_id BIGINT NOT NULL,
    amount NUMERIC(10,2) NOT NULL,
    type VARCHAR(20) NOT NULL,
    description VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_billing_tx_user_id ON billing_transactions(user_id);
CREATE INDEX idx_billing_tx_app_id ON billing_transactions(application_id);