CREATE TABLE outbox_event (
    id             VARCHAR(36) PRIMARY KEY,
    aggregate_type VARCHAR(255) NOT NULL,
    aggregate_id   VARCHAR(255) NOT NULL,
    type           VARCHAR(255) NOT NULL,
    destination    VARCHAR(255) NOT NULL,
    payload        JSON NOT NULL,
    trace_id       VARCHAR(255),
    created_at     DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
);

CREATE INDEX outbox_event_aggregate_idx ON outbox_event (aggregate_type, aggregate_id);
