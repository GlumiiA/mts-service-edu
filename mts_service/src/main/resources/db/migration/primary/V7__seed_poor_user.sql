-- V7: seed test user with low balance for insufficient-funds scenario testing
INSERT INTO users (id, email, name, password_hash, role) VALUES
    (1000, 'pooruser@test.com', 'Тест малый баланс',
     '$2a$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2.uheWG/igi', 'USER')
ON CONFLICT (email) DO NOTHING;

-- Advance sequence past the manually inserted ID to avoid future conflicts
SELECT setval('users_id_seq', GREATEST(1000, (SELECT MAX(id) FROM users)));