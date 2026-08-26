-- V3: seed test users and balances

INSERT INTO users (email, name, password_hash, role) VALUES
    ('user@test.com', 'Тестовый Пользователь', '$2a$10$dummyhashfordevonly000000000000000000000000', 'USER'),
    ('admin@test.com', 'Администратор', '$2a$10$dummyhashfordevonly000000000000000000000000', 'ADMIN'),
    ('manager@test.com', 'Менеджер', '$2a$10$dummyhashfordevonly000000000000000000000000', 'MANAGER');

INSERT INTO balances (user_id, amount) VALUES
    (1, 50000.00),
    (2, 99999.00),
    (3, 99999.00);
