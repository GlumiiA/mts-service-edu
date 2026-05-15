-- flyway:executeInTransaction=false

ALTER TYPE application_status_enum ADD VALUE IF NOT EXISTS 'PROCESSING';
ALTER TYPE application_status_enum ADD VALUE IF NOT EXISTS 'FAILED_EXTERNAL';
