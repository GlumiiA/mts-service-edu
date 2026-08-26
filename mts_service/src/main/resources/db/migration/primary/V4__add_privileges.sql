-- V4: add privilege enum and fix user password hashes for HTTP Basic auth
--     Privilege -> Role mapping lives in Java (Role enum with Set<Privilege>),
--     so no role_privileges table is needed in the DB.

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'privilege_enum') THEN
        CREATE TYPE privilege_enum AS ENUM (
            'CATALOG_READ',
            'APPLICATION_CREATE',
            'APPLICATION_READ_OWN',
            'APPLICATION_READ_ALL',
            'APPLICATION_APPROVE',
            'APPLICATION_REJECT',
            'ACCOUNT_TOPUP',
            'TARIFF_MANAGE',
            'SERVICE_MANAGE',
            'USER_MANAGE',
            'SEARCH_REINDEX'
        );
    END IF;
END$$;

-- Fix dummy password hashes to valid BCrypt of "password"
-- BCrypt hash: $2a$10$... for plaintext "password"
UPDATE users SET password_hash = '$2a$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2.uheWG/igi'
WHERE email IN ('user@test.com', 'admin@test.com', 'manager@test.com');