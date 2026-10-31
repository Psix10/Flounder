CREATE TABLE protocol_rule_sets (
    id UUID PRIMARY KEY,
    sport_id UUID NOT NULL REFERENCES sports(id),
    code VARCHAR(64) NOT NULL,
    name VARCHAR(255) NOT NULL,
    competition_format VARCHAR(64) NOT NULL,
    unit_type VARCHAR(64) NOT NULL,
    created_by_user_id UUID NOT NULL REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT uq_protocol_rule_sets_sport_code
        UNIQUE (sport_id, code),

    CONSTRAINT ck_protocol_rule_sets_code
        CHECK (length(btrim(code)) > 0),

    CONSTRAINT ck_protocol_rule_sets_name
        CHECK (length(btrim(name)) > 0)
);

CREATE TABLE protocol_rule_set_versions (
    id UUID PRIMARY KEY,
    rule_set_id UUID NOT NULL REFERENCES protocol_rule_sets(id),
    version_no INTEGER NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    created_by_user_id UUID NOT NULL REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    published_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT uq_protocol_rule_set_version_number
        UNIQUE (rule_set_id, version_no),

    CONSTRAINT ck_protocol_rule_set_version_number
        CHECK (version_no > 0),

    CONSTRAINT ck_protocol_rule_set_version_status
        CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED')),

    CONSTRAINT ck_protocol_rule_set_published_at
        CHECK (
            status <> 'PUBLISHED'
            OR published_at IS NOT NULL
        )
);

CREATE TABLE protocol_rule_blocks (
    id UUID PRIMARY KEY,
    rule_set_version_id UUID NOT NULL
        REFERENCES protocol_rule_set_versions(id),
    order_index INTEGER NOT NULL,
    executor_code VARCHAR(100) NOT NULL,
    config_json JSONB NOT NULL DEFAULT '{}'::JSONB,

    CONSTRAINT uq_protocol_rule_block_order
        UNIQUE (rule_set_version_id, order_index),

    CONSTRAINT ck_protocol_rule_block_order
        CHECK (order_index >= 0),

    CONSTRAINT ck_protocol_rule_block_executor_code
        CHECK (length(btrim(executor_code)) > 0),

    CONSTRAINT ck_protocol_rule_block_config
        CHECK (jsonb_typeof(config_json) = 'object')
);

CREATE TABLE start_protocol_snapshots (
    id UUID PRIMARY KEY,
    event_discipline_id UUID NOT NULL
        REFERENCES event_disciplines(id),
    rule_set_version_id UUID NOT NULL
        REFERENCES protocol_rule_set_versions(id),
    revision_no INTEGER NOT NULL,
    operation VARCHAR(32) NOT NULL,
    input_json JSONB NOT NULL,
    protocol_json JSONB NOT NULL,
    created_by_user_id UUID NOT NULL REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_start_protocol_snapshot_revision
        UNIQUE (event_discipline_id, revision_no),

    CONSTRAINT ck_start_protocol_snapshot_revision
        CHECK (revision_no > 0),

    CONSTRAINT ck_start_protocol_snapshot_operation
        CHECK (operation IN ('GENERATE', 'REGENERATE')),

    CONSTRAINT ck_start_protocol_snapshot_input
        CHECK (jsonb_typeof(input_json) = 'object'),

    CONSTRAINT ck_start_protocol_snapshot_protocol
        CHECK (jsonb_typeof(protocol_json) = 'object')
);

CREATE INDEX idx_protocol_rule_set_versions_status
    ON protocol_rule_set_versions(rule_set_id, status);

CREATE INDEX idx_start_protocol_snapshots_rule_version
    ON start_protocol_snapshots(rule_set_version_id);