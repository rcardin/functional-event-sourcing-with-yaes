CREATE TABLE events (
  aggregate_id   TEXT        NOT NULL,
  aggregate_type TEXT        NOT NULL,
  sequence_no    BIGINT      NOT NULL,
  event_type     TEXT        NOT NULL,
  payload        JSONB       NOT NULL,
  occurred_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (aggregate_id, aggregate_type, sequence_no)
);
