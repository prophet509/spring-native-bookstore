CREATE TABLE IF NOT EXISTS catalog_book_snapshot (
    isbn        TEXT PRIMARY KEY,
    title       TEXT NOT NULL,
    price       NUMERIC(10,2) NOT NULL,
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    event_ts    TIMESTAMPTZ
);
