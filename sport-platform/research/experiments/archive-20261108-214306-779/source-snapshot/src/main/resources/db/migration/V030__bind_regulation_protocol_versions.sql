create table regulation_protocol_bindings (
    regulation_version_id uuid primary key references regulation_versions(id),
    protocol_rule_set_version_id uuid not null references protocol_rule_set_versions(id),
    competition_format varchar(64) not null,
    unit_type varchar(64) not null,
    created_by_user_id uuid not null references users(id),
    created_at timestamptz not null default now()
);
create index idx_regulation_protocol_binding_rule on regulation_protocol_bindings(protocol_rule_set_version_id);
