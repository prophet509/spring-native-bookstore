ALTER TABLE book
    ADD COLUMN publisher VARCHAR(255);

UPDATE book SET publisher = 'Polarsophia' WHERE publisher IS NULL;

ALTER TABLE book
    MODIFY COLUMN publisher VARCHAR(255) NOT NULL;
