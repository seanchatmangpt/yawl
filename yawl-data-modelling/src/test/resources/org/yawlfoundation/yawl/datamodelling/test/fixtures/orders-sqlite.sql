CREATE TABLE orders (
    order_id     INTEGER PRIMARY KEY,
    customer_id  INTEGER NOT NULL,
    total_amount REAL    NOT NULL,
    status       TEXT    NOT NULL,
    created_at   TEXT    NOT NULL
);

CREATE TABLE order_items (
    item_id    INTEGER PRIMARY KEY,
    order_id   INTEGER NOT NULL,
    product_id INTEGER,
    quantity   INTEGER,
    unit_price REAL
);
