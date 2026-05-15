-- V14: Fix QRTZ_LOCKS table schema - remove invalid columns
-- The QRTZ_LOCKS table per Quartz standard should ONLY have SCHED_NAME and LOCK_NAME columns
-- INSTANCE_NAME and LOCK_TIME are not part of the official schema

-- Clear all locks first (safe operation - they're just session markers)
TRUNCATE TABLE QRTZ_LOCKS;

-- Remove INSTANCE_NAME column if it exists
DO $$
BEGIN
  IF EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_name='qrtz_locks' AND column_name='instance_name'
  ) THEN
    ALTER TABLE QRTZ_LOCKS DROP COLUMN INSTANCE_NAME;
  END IF;
END $$;

-- Remove LOCK_TIME column if it exists
DO $$
BEGIN
  IF EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_name='qrtz_locks' AND column_name='lock_time'
  ) THEN
    ALTER TABLE QRTZ_LOCKS DROP COLUMN LOCK_TIME;
  END IF;
END $$;




