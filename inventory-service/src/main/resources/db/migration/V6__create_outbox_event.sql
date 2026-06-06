CREATE TABLE outbox_event (
    id             UUID PRIMARY KEY,
    aggregate_type TEXT NOT NULL,
    aggregate_id   TEXT NOT NULL,
    type           TEXT NOT NULL,
    destination    TEXT NOT NULL,
    payload        JSONB NOT NULL,
    trace_id       TEXT,
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE INDEX outbox_event_aggregate_idx ON outbox_event (aggregate_type, aggregate_id);
