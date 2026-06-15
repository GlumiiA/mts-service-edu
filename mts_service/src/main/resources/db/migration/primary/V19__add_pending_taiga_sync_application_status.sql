-- flyway:executeInTransaction=false

ALTER TYPE application_status_enum ADD VALUE IF NOT EXISTS 'PENDING_TAIGA_SYNC';
