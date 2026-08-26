-- V6: add locked_price to applications (price fixed at creation time)
ALTER TABLE applications ADD COLUMN locked_price NUMERIC(19,2) NOT NULL DEFAULT 0;