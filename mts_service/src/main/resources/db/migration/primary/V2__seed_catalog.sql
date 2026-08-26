-- V2: seed catalog data for development/testing

-- Cities
INSERT INTO cities (code, name, region) VALUES
    ('MSK', 'Москва', 'Московская область'),
    ('SPB', 'Санкт-Петербург', 'Ленинградская область'),
    ('EKB', 'Екатеринбург', 'Свердловская область')
ON CONFLICT (code) DO NOTHING;

-- Additional services
INSERT INTO services (name, price, description) VALUES
    ('Антивирус Kaspersky', 99.00, 'Защита всех устройств в домашней сети'),
    ('Родительский контроль', 49.00, 'Ограничение доступа к сайтам для детей'),
    ('Статический IP', 150.00, 'Выделенный статический IP-адрес'),
    ('Облачное хранилище 100 ГБ', 79.00, 'Хранение и синхронизация файлов в облаке');

-- HOME tariffs (price varies by city via tariff_city_prices)
INSERT INTO tariffs (name, category, status, description, speed_mbps, tv_channels, created_at, updated_at) VALUES
    ('Домашний Лайт', 'HOME', 'ACTIVE', 'Быстрый интернет и базовый пакет ТВ', 100, 120, NOW(), NOW()),
    ('Домашний Плюс', 'HOME', 'ACTIVE', 'Высокоскоростной интернет и расширенный пакет ТВ', 300, 200, NOW(), NOW()),
    ('Домашний Макс', 'HOME', 'ACTIVE', 'Максимальная скорость и полный пакет ТВ', 1000, 300, NOW(), NOW());

-- MOBILE tariffs
INSERT INTO tariffs (name, category, base_price, status, description, internet_gb, calls_minutes, sms_count, for_family, no_subscription_fee, created_at, updated_at) VALUES
    ('Мобильный Старт', 'MOBILE', 350.00, 'ACTIVE', 'Базовый мобильный тариф', 5, 300, 100, false, false, NOW(), NOW()),
    ('Мобильный Семья', 'MOBILE', 600.00, 'ACTIVE', 'Выгодный тариф для всей семьи', 20, 1000, 200, true, false, NOW(), NOW()),
    ('Мобильный Без Подписки', 'MOBILE', 0.00, 'ACTIVE', 'Платите только за то, чем пользуетесь', 3, 100, 50, false, true, NOW(), NOW());

-- DEVICES tariffs
INSERT INTO tariffs (name, category, base_price, status, description, internet_gb, device_type, created_at, updated_at) VALUES
    ('Часы Онлайн', 'DEVICES', 199.00, 'ACTIVE', 'SIM-карта для умных часов', 2, 'WATCH', NOW(), NOW()),
    ('Модем Стандарт', 'DEVICES', 299.00, 'ACTIVE', 'Мобильный интернет для модема', 30, 'MODEM', NOW(), NOW()),
    ('Модем Макс', 'DEVICES', 499.00, 'ACTIVE', 'Безлимитный интернет для модема', 100, 'MODEM', NOW(), NOW());

-- LANDLINE tariffs (price varies by city)
INSERT INTO tariffs (name, category, status, description, local_minutes, intercity_minutes, mobile_minutes, created_at, updated_at) VALUES
    ('Домашний Телефон Базовый', 'LANDLINE', 'ACTIVE', 'Звонки по городу без ограничений', 1000, 60, 30, NOW(), NOW()),
    ('Домашний Телефон Плюс', 'LANDLINE', 'ACTIVE', 'Звонки по городу и межгород', 1000, 300, 100, NOW(), NOW());

-- SATELLITE_TV tariffs (price varies by city)
INSERT INTO tariffs (name, category, status, description, channels_total, channels_hd, created_at, updated_at) VALUES
    ('Спутник Базовый', 'SATELLITE_TV', 'ACTIVE', 'Базовый пакет спутникового ТВ', 150, 20, NOW(), NOW()),
    ('Спутник Премиум', 'SATELLITE_TV', 'ACTIVE', 'Полный пакет с HD каналами', 300, 80, NOW(), NOW());

-- BUSINESS tariffs
INSERT INTO tariffs (name, category, base_price, status, description, speed_mbps, sla_description, created_at, updated_at) VALUES
    ('Бизнес Старт', 'BUSINESS', 1500.00, 'ACTIVE', 'Надёжный интернет для малого бизнеса', 100, 'Восстановление за 8 часов, аптайм 99%', NOW(), NOW()),
    ('Бизнес Про', 'BUSINESS', 3500.00, 'ACTIVE', 'Корпоративный интернет с гарантированной скоростью', 500, 'Восстановление за 4 часа, аптайм 99.9%', NOW(), NOW());

-- Tariff-service bindings (HOME Лайт gets антивирус + родительский контроль)
INSERT INTO tariff_services (tariff_id, service_id)
SELECT t.id, s.id
FROM tariffs t, services s
WHERE t.name = 'Домашний Лайт' AND s.name IN ('Антивирус Kaspersky', 'Родительский контроль');

-- HOME Плюс gets all services
INSERT INTO tariff_services (tariff_id, service_id)
SELECT t.id, s.id
FROM tariffs t, services s
WHERE t.name = 'Домашний Плюс';

-- BUSINESS gets статический IP + облако
INSERT INTO tariff_services (tariff_id, service_id)
SELECT t.id, s.id
FROM tariffs t, services s
WHERE t.name IN ('Бизнес Старт', 'Бизнес Про')
  AND s.name IN ('Статический IP', 'Облачное хранилище 100 ГБ');

-- City prices for HOME tariffs (MSK=1, SPB=2, EKB=3)
INSERT INTO tariff_city_prices (tariff_id, city_id, price)
SELECT t.id, c.id,
    CASE
        WHEN t.name = 'Домашний Лайт'  AND c.code = 'MSK' THEN 499.00
        WHEN t.name = 'Домашний Лайт'  AND c.code = 'SPB' THEN 449.00
        WHEN t.name = 'Домашний Лайт'  AND c.code = 'EKB' THEN 399.00
        WHEN t.name = 'Домашний Плюс'  AND c.code = 'MSK' THEN 799.00
        WHEN t.name = 'Домашний Плюс'  AND c.code = 'SPB' THEN 749.00
        WHEN t.name = 'Домашний Плюс'  AND c.code = 'EKB' THEN 699.00
        WHEN t.name = 'Домашний Макс'  AND c.code = 'MSK' THEN 1199.00
        WHEN t.name = 'Домашний Макс'  AND c.code = 'SPB' THEN 1099.00
        WHEN t.name = 'Домашний Макс'  AND c.code = 'EKB' THEN 999.00
    END
FROM tariffs t
CROSS JOIN cities c
WHERE t.category = 'HOME';

-- City prices for LANDLINE tariffs
INSERT INTO tariff_city_prices (tariff_id, city_id, price)
SELECT t.id, c.id,
    CASE
        WHEN t.name = 'Домашний Телефон Базовый' AND c.code = 'MSK' THEN 250.00
        WHEN t.name = 'Домашний Телефон Базовый' AND c.code = 'SPB' THEN 230.00
        WHEN t.name = 'Домашний Телефон Базовый' AND c.code = 'EKB' THEN 200.00
        WHEN t.name = 'Домашний Телефон Плюс'    AND c.code = 'MSK' THEN 450.00
        WHEN t.name = 'Домашний Телефон Плюс'    AND c.code = 'SPB' THEN 420.00
        WHEN t.name = 'Домашний Телефон Плюс'    AND c.code = 'EKB' THEN 380.00
    END
FROM tariffs t
CROSS JOIN cities c
WHERE t.category = 'LANDLINE';

-- City prices for SATELLITE_TV tariffs
INSERT INTO tariff_city_prices (tariff_id, city_id, price)
SELECT t.id, c.id,
    CASE
        WHEN t.name = 'Спутник Базовый'  AND c.code = 'MSK' THEN 399.00
        WHEN t.name = 'Спутник Базовый'  AND c.code = 'SPB' THEN 379.00
        WHEN t.name = 'Спутник Базовый'  AND c.code = 'EKB' THEN 349.00
        WHEN t.name = 'Спутник Премиум'  AND c.code = 'MSK' THEN 799.00
        WHEN t.name = 'Спутник Премиум'  AND c.code = 'SPB' THEN 749.00
        WHEN t.name = 'Спутник Премиум'  AND c.code = 'EKB' THEN 699.00
    END
FROM tariffs t
CROSS JOIN cities c
WHERE t.category = 'SATELLITE_TV';
