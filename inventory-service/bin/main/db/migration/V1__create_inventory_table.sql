CREATE TABLE inventory (
    id               BIGSERIAL PRIMARY KEY,
    isbn             VARCHAR(255) NOT NULL UNIQUE,
    available_quantity INT NOT NULL DEFAULT 0,
    reserved_quantity  INT NOT NULL DEFAULT 0,
    version          BIGINT NOT NULL DEFAULT 0
);
