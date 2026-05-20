ALTER TABLE reservation DROP CONSTRAINT IF EXISTS uq_reservation_order_isbn;

DELETE FROM reservation;

ALTER TABLE reservation
    ALTER COLUMN order_id TYPE BIGINT USING NULL;

ALTER TABLE reservation
    ALTER COLUMN order_id SET NOT NULL;

ALTER TABLE reservation
    ADD CONSTRAINT uq_reservation_order_isbn UNIQUE (order_id, isbn);
