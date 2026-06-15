CREATE UNIQUE INDEX IF NOT EXISTS ux_billing_tx_application_debit
    ON billing_transactions(application_id)
    WHERE type = 'DEBIT';
