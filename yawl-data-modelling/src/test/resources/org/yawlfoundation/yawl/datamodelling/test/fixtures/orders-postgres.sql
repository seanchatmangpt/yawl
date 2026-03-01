CREATE TABLE orders (
    order_id      INTEGER      NOT NULL PRIMARY KEY,
    customer_id   INTEGER      NOT NULL,
    total_amount  DECIMAL      NOT NULL,
    status        VARCHAR(50)  NOT NULL,
    created_at    TIMESTAMP    NOT NULL
);

CREATE TABLE order_items (
    item_id     INTEGER NOT NULL PRIMARY KEY,
    order_id    INTEGER NOT NULL REFERENCES orders(order_id),
    product_id  INTEGER,
    quantity    INTEGER,
    unit_price  DECIMAL
);
