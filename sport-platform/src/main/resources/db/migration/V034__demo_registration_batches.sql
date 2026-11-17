CREATE TABLE demo_registration_batches (
    request_id uuid PRIMARY KEY,
    event_id uuid NOT NULL REFERENCES events(id),
    event_discipline_id uuid NOT NULL UNIQUE REFERENCES event_disciplines(id),
    actor_user_id uuid NOT NULL REFERENCES users(id),
    rule_version_id uuid NOT NULL,
    capacity integer NOT NULL CHECK (capacity BETWEEN 1 AND 32),
    participant_count integer NOT NULL CHECK (participant_count BETWEEN 2 AND 64),
    response_json jsonb NOT NULL DEFAULT '{}'::jsonb,
    created_at timestamptz NOT NULL DEFAULT now(),
    CHECK (participant_count = capacity * 2)
);
