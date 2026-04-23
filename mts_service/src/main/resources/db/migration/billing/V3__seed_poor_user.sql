-- V3: seed balance for pooruser@test.com (user_id=1000, balance=500)
-- Used for testing insufficient-funds scenario: two applications at 350 each,
-- first approve succeeds (500→150), second fails with 402 (150 < 350).
INSERT INTO balances (user_id, amount) VALUES (1000, 500.00)
ON CONFLICT (user_id) DO NOTHING;