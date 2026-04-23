-- V5: remove balances table from primary DB (moved to billing_db)
DROP INDEX IF EXISTS idx_balances_user_id;
ALTER TABLE balances DROP CONSTRAINT IF EXISTS fk_balances_user;
DROP TABLE IF EXISTS balances;