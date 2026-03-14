ALTER TABLE book
    ADD COLUMN IF NOT EXISTS publisher VARCHAR(255);

UPDATE book SET publisher = 'Polarsophia' WHERE publisher IS NULL;

ALTER TABLE book
    ALTER COLUMN publisher SET NOT NULL;
