-- V1: core schema for flashdrop (products, drops, inventory, orders).
-- Flyway runs this file once and records it in flyway_schema_history.
-- Never edit this file after it has run. Schema changes go in a new V2__..., V3__... file.

-- What is being sold. Changes rarely, read often.
CREATE TABLE products (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name        VARCHAR(200) NOT NULL,
    brand       VARCHAR(100) NOT NULL,
    description TEXT,
    image_url   VARCHAR(500),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE drops (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    product_id  BIGINT NOT NULL REFERENCES products(id),
    name        VARCHAR(200) NOT NULL,
    price       NUMERIC(10,2) NOT NULL CHECK (price > 0),
    starts_at   TIMESTAMPTZ  NOT NULL,
    ends_at     TIMESTAMPTZ  NOT NULL,
    max_per_user INTEGER NOT NULL DEFAULT 1 CHECK (max_per_user > 0),
    status       VARCHAR(20) NOT NULL CHECK (status IN ('SCHEDULED', 'LIVE', 'ENDED', 'CANCELLED')),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (ends_at > starts_at)
);

CREATE TABLE inventory(
    drop_id         BIGINT PRIMARY KEY REFERENCES drops(id),
    total_units     INTEGER NOT NULL CHECK (total_units > 0),
    available_units INTEGER NOT NULL,
    CHECK (available_units >= 0 AND available_units <= total_units)
);

CREATE TABLE orders(
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    drop_id    BIGINT NOT NULL REFERENCES drops(id),
    user_id    BIGINT NOT NULL,
    quantity   INTEGER NOT NULL CHECK (quantity > 0 ),
    unit_price NUMERIC(10,2) NOT NULL,
    status     VARCHAR(20) NOT NULL CHECK (status IN ('PENDING_PAYMENT', 'CONFIRMED', 'CANCELLED', 'FAILED')),
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_orders_user_drop ON orders (user_id, drop_id);
