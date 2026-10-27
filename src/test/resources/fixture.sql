-- Fixture schema exercising the things the ERD renders: PK/FK/unique, nullable FK, composite PK,
-- self-reference, disconnected table, view, materialized view, comments, assorted types.
CREATE SCHEMA IF NOT EXISTS shop;
SET search_path TO shop;

CREATE TYPE mood AS ENUM ('happy', 'sad');

CREATE TABLE customers (
    id          serial PRIMARY KEY,
    email       varchar(255) NOT NULL UNIQUE,
    full_name   text,
    created_at  timestamptz NOT NULL DEFAULT now(),
    active      boolean NOT NULL DEFAULT true,
    mood        mood,
    tags        text[],
    ext_id      uuid
);
COMMENT ON TABLE customers IS 'People who buy things';
COMMENT ON COLUMN customers.email IS 'login name';

CREATE TABLE categories (
    id        serial PRIMARY KEY,
    parent_id integer REFERENCES categories (id),
    name      varchar(100) NOT NULL
);

CREATE TABLE products (
    id          serial PRIMARY KEY,
    sku         varchar(32) NOT NULL,
    name        varchar(200) NOT NULL,
    category_id integer REFERENCES categories (id),
    price       numeric(12, 2) NOT NULL,
    weight      numeric(8),
    data        jsonb,
    picture     bytea,
    CONSTRAINT products_sku_key UNIQUE (sku)
);

CREATE TABLE orders (
    id          bigserial PRIMARY KEY,
    customer_id integer NOT NULL REFERENCES customers (id),
    placed_at   timestamp(3) NOT NULL,
    total       numeric(10, 2) NOT NULL,
    notes       jsonb,
    delivery    interval day to second(3),
    opening     timetz(0)
);
COMMENT ON CONSTRAINT orders_customer_id_fkey ON orders IS 'who ordered';

CREATE TABLE order_items (
    order_id   bigint NOT NULL REFERENCES orders (id),
    product_id integer NOT NULL REFERENCES products (id),
    quantity   integer NOT NULL,
    PRIMARY KEY (order_id, product_id)
);

CREATE TABLE addresses (
    id          serial PRIMARY KEY,
    customer_id integer NOT NULL REFERENCES customers (id),
    line1       varchar(255) NOT NULL,
    city        varchar(100) NOT NULL,
    geo         point
);
CREATE UNIQUE INDEX addresses_one_per_customer ON addresses (customer_id);

CREATE TABLE audit_log (
    id        bigserial PRIMARY KEY,
    payload   jsonb NOT NULL,
    logged_at timestamptz NOT NULL
);

CREATE TABLE settings (
    key   varchar(64) NOT NULL,
    value text,
    CONSTRAINT settings_key_uq UNIQUE (key)
);

CREATE TABLE no_keys_at_all (
    a integer,
    b bit(8),
    c varbit(3)
);

CREATE VIEW v_customer_orders AS
    SELECT c.id AS customer_id, count(o.id) AS order_count FROM customers c LEFT JOIN orders o ON o.customer_id = c.id GROUP BY c.id;

CREATE MATERIALIZED VIEW mv_totals AS SELECT customer_id, sum(total) AS total FROM orders GROUP BY customer_id;
