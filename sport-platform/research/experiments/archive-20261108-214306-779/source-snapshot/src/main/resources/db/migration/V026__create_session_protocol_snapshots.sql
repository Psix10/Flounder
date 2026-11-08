CREATE TABLE session_protocol_snapshots (
    id uuid PRIMARY KEY,
    event_id uuid NOT NULL,
    revision_no integer NOT NULL CHECK (revision_no > 0),
    input_json jsonb NOT NULL,
    protocol_json jsonb NOT NULL,
    created_by_user_id uuid NOT NULL,
    created_at timestamptz NOT NULL,
    CONSTRAINT uq_session_protocol_snapshots_event_revision UNIQUE (event_id, revision_no)
);
