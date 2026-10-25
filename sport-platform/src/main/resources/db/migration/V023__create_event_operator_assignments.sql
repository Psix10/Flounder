CREATE TABLE event_operator_assignments (
    id UUID PRIMARY KEY,
    event_id UUID NOT NULL,
    operator_user_id UUID NOT NULL,
    assigned_by_user_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,

    CONSTRAINT fk_event_operator_assignments_event
        FOREIGN KEY (event_id)
        REFERENCES events (id)
        ON DELETE CASCADE,

    CONSTRAINT fk_event_operator_assignments_operator
        FOREIGN KEY (operator_user_id)
        REFERENCES users (id),

    CONSTRAINT fk_event_operator_assignments_assigned_by
        FOREIGN KEY (assigned_by_user_id)
        REFERENCES users (id),

    CONSTRAINT uq_event_operator_assignments_event_user
        UNIQUE (event_id, operator_user_id)
);

CREATE INDEX idx_event_operator_assignments_operator
    ON event_operator_assignments (operator_user_id);