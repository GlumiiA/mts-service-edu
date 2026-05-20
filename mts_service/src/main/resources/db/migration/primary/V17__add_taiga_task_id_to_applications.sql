ALTER TABLE applications
    ADD COLUMN IF NOT EXISTS taiga_task_id BIGINT;

CREATE INDEX IF NOT EXISTS idx_app_taiga_task_id
    ON applications(taiga_task_id);
