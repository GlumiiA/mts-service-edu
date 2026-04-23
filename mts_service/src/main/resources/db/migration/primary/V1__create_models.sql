-- V1: create all model tables for the application
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'role_enum') THEN
        CREATE TYPE role_enum AS ENUM ('USER','ADMIN','MANAGER');
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'tariff_category_enum') THEN
        CREATE TYPE tariff_category_enum AS ENUM ('HOME','MOBILE','DEVICES','LANDLINE','SATELLITE_TV','BUSINESS');
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'tariff_status_enum') THEN
        CREATE TYPE tariff_status_enum AS ENUM ('ACTIVE','ARCHIVED');
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'application_status_enum') THEN
        CREATE TYPE application_status_enum AS ENUM ('PENDING','APPROVED','REJECTED','CONNECTED');
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'device_type_enum') THEN
        CREATE TYPE device_type_enum AS ENUM ('WATCH','MODEM');

    END IF;
END$$;

CREATE TABLE IF NOT EXISTS users (
  id BIGSERIAL PRIMARY KEY,
  email VARCHAR(255) NOT NULL UNIQUE,
  name VARCHAR(100) NOT NULL,
  password_hash VARCHAR(255) NOT NULL,
  role role_enum NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS cities (
  id BIGSERIAL PRIMARY KEY,
  code VARCHAR(50) NOT NULL UNIQUE,
  name VARCHAR(100) NOT NULL,
  region VARCHAR(150)
);

CREATE TABLE IF NOT EXISTS services (
  id BIGSERIAL PRIMARY KEY,
  name VARCHAR(100) NOT NULL,
  price NUMERIC(10,2) NOT NULL,
  description VARCHAR(2048) NOT NULL
);

CREATE TABLE IF NOT EXISTS tariffs (
  id BIGSERIAL PRIMARY KEY,
  name VARCHAR(150) NOT NULL,
  category tariff_category_enum NOT NULL,
  base_price NUMERIC(10,2),
  status tariff_status_enum NOT NULL DEFAULT 'ACTIVE',
  description VARCHAR(2048),
  speed_mbps INTEGER,
  tv_channels INTEGER,
  internet_gb INTEGER,
  calls_minutes INTEGER,
  sms_count INTEGER,
  for_family BOOLEAN NOT NULL DEFAULT FALSE,
  no_subscription_fee BOOLEAN NOT NULL DEFAULT FALSE,
  device_type device_type_enum,
  local_minutes INTEGER,
  intercity_minutes INTEGER,
  mobile_minutes INTEGER,
  channels_total INTEGER,
  channels_hd INTEGER,
  sla_description VARCHAR(2048),
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- many-to-many тарифы-услуги
CREATE TABLE IF NOT EXISTS tariff_services (
  tariff_id BIGINT NOT NULL,
  service_id BIGINT NOT NULL,
  PRIMARY KEY (tariff_id, service_id),
  CONSTRAINT fk_ts_tariff FOREIGN KEY (tariff_id) REFERENCES tariffs(id),
  CONSTRAINT fk_ts_service FOREIGN KEY (service_id) REFERENCES services(id)
);

CREATE TABLE IF NOT EXISTS tariff_city_prices (
  id BIGSERIAL PRIMARY KEY,
  tariff_id BIGINT NOT NULL,
  city_id BIGINT NOT NULL,
  price NUMERIC(10,2) NOT NULL,
  CONSTRAINT uq_tariff_city UNIQUE (tariff_id, city_id),
  CONSTRAINT fk_tcp_tariff FOREIGN KEY (tariff_id) REFERENCES tariffs(id),
  CONSTRAINT fk_tcp_city FOREIGN KEY (city_id) REFERENCES cities(id)
);

CREATE TABLE IF NOT EXISTS balances (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL,
  amount NUMERIC(10,2) NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT fk_balances_user FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE TABLE IF NOT EXISTS applications (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL,
  tariff_id BIGINT NOT NULL,
  address VARCHAR(500) NOT NULL,
  status application_status_enum NOT NULL DEFAULT 'PENDING',
  reject_reason VARCHAR(2048),
  passport_verified BOOLEAN NOT NULL DEFAULT FALSE,
  technical_feasibility BOOLEAN NOT NULL DEFAULT FALSE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT fk_app_user FOREIGN KEY (user_id) REFERENCES users(id),
  CONSTRAINT fk_app_tariff FOREIGN KEY (tariff_id) REFERENCES tariffs(id)
);

-- many-to-many заявления-услуги
CREATE TABLE IF NOT EXISTS application_services (
  application_id BIGINT NOT NULL,
  service_id BIGINT NOT NULL,
  PRIMARY KEY (application_id, service_id),
  CONSTRAINT fk_as_application FOREIGN KEY (application_id) REFERENCES applications(id),
  CONSTRAINT fk_as_service FOREIGN KEY (service_id) REFERENCES services(id)
);

CREATE INDEX IF NOT EXISTS idx_balances_user_id ON balances(user_id);
CREATE INDEX IF NOT EXISTS idx_app_user_id ON applications(user_id);
CREATE INDEX IF NOT EXISTS idx_app_tariff_id ON applications(tariff_id);
CREATE INDEX IF NOT EXISTS idx_tcp_tariff_id ON tariff_city_prices(tariff_id);
CREATE INDEX IF NOT EXISTS idx_tcp_city_id ON tariff_city_prices(city_id);
CREATE INDEX IF NOT EXISTS idx_ts_service_id ON tariff_services(service_id);
CREATE INDEX IF NOT EXISTS idx_as_service_id ON application_services(service_id);

