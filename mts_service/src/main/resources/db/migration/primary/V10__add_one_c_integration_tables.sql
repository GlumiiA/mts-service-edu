-- V10: Add 1C integration tables and enums
DO $$
BEGIN
    -- Create ENUM types if they don't exist
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'sync_status_enum') THEN
        CREATE TYPE sync_status_enum AS ENUM ('PENDING', 'SUCCESS', 'INSUFFICIENT_FUNDS', 'REJECTED', 'RETRY', 'DLQ');
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'sync_direction_enum') THEN
        CREATE TYPE sync_direction_enum AS ENUM ('TO_1C', 'FROM_1C');
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'error_detail_enum') THEN
        CREATE TYPE error_detail_enum AS ENUM ('INSUFFICIENT_FUNDS', 'TIMEOUT', 'VALIDATION_ERROR', 'NETWORK_ERROR', 'UNKNOWN_ERROR');
    END IF;
END$$;

-- Table for tracking sync history with 1C
CREATE TABLE IF NOT EXISTS one_c_sync_history (
    id BIGSERIAL PRIMARY KEY,
    entity_type VARCHAR(50) NOT NULL,                -- 'APPLICATION'
    entity_id BIGINT NOT NULL,                       -- Reference to applications.id
    external_id VARCHAR(100) UNIQUE,                 -- 1C unique identifier
    sync_status sync_status_enum NOT NULL DEFAULT 'PENDING',
    sync_direction sync_direction_enum NOT NULL,    -- TO_1C or FROM_1C
    retry_count INTEGER NOT NULL DEFAULT 0,
    max_retries INTEGER NOT NULL DEFAULT 5,
    last_error TEXT,
    last_sync_at TIMESTAMPTZ,
    next_retry_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_sync_history_application FOREIGN KEY (entity_id) REFERENCES applications(id)
);

-- Table for storing sync errors with detailed information
CREATE TABLE IF NOT EXISTS one_c_errors (
    id BIGSERIAL PRIMARY KEY,
    sync_history_id BIGINT NOT NULL,
    error_code VARCHAR(50),
    error_message TEXT NOT NULL,
    error_detail error_detail_enum NOT NULL,
    error_stacktrace TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_errors_sync_history FOREIGN KEY (sync_history_id) REFERENCES one_c_sync_history(id)
);

-- Table for tracking rejected applications
CREATE TABLE IF NOT EXISTS rejected_applications (
    id BIGSERIAL PRIMARY KEY,
    application_id BIGINT NOT NULL,
    sync_history_id BIGINT,
    rejection_reason VARCHAR(500),
    error_code VARCHAR(50),
    manual_review_required BOOLEAN NOT NULL DEFAULT TRUE,
    reviewed_at TIMESTAMPTZ,
    reviewed_by VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_rejected_app FOREIGN KEY (application_id) REFERENCES applications(id),
    CONSTRAINT fk_rejected_sync_history FOREIGN KEY (sync_history_id) REFERENCES one_c_sync_history(id)
);

-- Create indexes for efficient queries
CREATE INDEX IF NOT EXISTS idx_sync_status_next_retry
    ON one_c_sync_history (sync_status, next_retry_at)
    WHERE sync_status IN ('PENDING', 'RETRY');

CREATE INDEX IF NOT EXISTS idx_sync_entity
    ON one_c_sync_history (entity_type, entity_id);

CREATE INDEX IF NOT EXISTS idx_sync_external_id
    ON one_c_sync_history (external_id)
    WHERE external_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_sync_created_at
    ON one_c_sync_history (created_at DESC);

CREATE INDEX IF NOT EXISTS idx_error_sync_history
    ON one_c_errors (sync_history_id);

CREATE INDEX IF NOT EXISTS idx_error_detail
    ON one_c_errors (error_detail);

CREATE INDEX IF NOT EXISTS idx_rejected_application
    ON rejected_applications (application_id);

CREATE INDEX IF NOT EXISTS idx_rejected_manual_review
    ON rejected_applications (manual_review_required, created_at DESC);

-- Table for Quartz JDBC JobStore (will be created by spring.quartz)
-- But we ensure PostgreSQL can support distributed scheduling via XA

