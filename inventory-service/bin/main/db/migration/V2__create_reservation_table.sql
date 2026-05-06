CREATE TABLE reservation (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id      UUID NOT NULL,
    isbn          VARCHAR(255) NOT NULL,
    quantity      INT NOT NULL,
    status        VARCHAR(50) NOT NULL,
    created_at    TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT uq_reservation_order_isbn UNIQUE (order_id, isbn)
);
