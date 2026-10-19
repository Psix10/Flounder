-- src/main/resources/db/migration/V002__init_extensions.sql
CREATE EXTENSION IF NOT EXISTS pgcrypto;

create table if not exists users (

    id uuid primary key default gen_random_uuid(),

    email varchar(255) not null unique,

    phone varchar(32),

    password_hash text not null,

    status varchar(32) not null,

    created_at timestamptz not null default now(),

    updated_at timestamptz not null default now()

);



create table if not exists profiles (

    id uuid primary key default gen_random_uuid(),

    user_id uuid not null unique references users(id) on delete cascade,

    first_name varchar(128) not null,

    last_name varchar(128) not null,

    middle_name varchar(128),

    birth_date date not null,

    gender varchar(16),

    city varchar(128),

    country_code varchar(8),

    club_name varchar(255),

    sport_meta jsonb not null default '{}'::jsonb,

    created_at timestamptz not null default now(),

    updated_at timestamptz not null default now()

);



create table if not exists roles (

    id uuid primary key default gen_random_uuid(),

    code varchar(64) not null unique,

    name varchar(128) not null

);



create table if not exists user_role_assignments (

    id uuid primary key default gen_random_uuid(),

    user_id uuid not null references users(id) on delete cascade,

    role_id uuid not null references roles(id) on delete cascade,

    event_id uuid,

    organization_id uuid,

    created_at timestamptz not null default now()

);



create index if not exists idx_users_status on users(status);

create index if not exists idx_user_role_assignments_user_id on user_role_assignments(user_id);

create index if not exists idx_user_role_assignments_role_id on user_role_assignments(role_id);



insert into roles (code, name)

values

    ('platform_admin', 'Platform Admin'),

    ('organizer', 'Organizer'),

    ('operator', 'Operator'),

    ('participant', 'Participant')

on conflict (code) do nothing;



insert into users (

    id,

    email,

    phone,

    password_hash,

    status,

    created_at,

    updated_at

)

values (

    '11111111-1111-1111-1111-111111111111',

    'admin@sport-platform.local',

    null,

    '$2a$10$REPLACE_WITH_REAL_BCRYPT_HASH',

    'active',

    now(),

    now()

)

on conflict (email) do nothing;



insert into profiles (

    id,

    user_id,

    first_name,

    last_name,

    middle_name,

    birth_date,

    gender,

    city,

    country_code,

    club_name,

    sport_meta,

    created_at,

    updated_at

)

values (

    '22222222-2222-2222-2222-222222222222',

    '11111111-1111-1111-1111-111111111111',

    'Platform',

    'Admin',

    null,

    date '1990-01-01',

    null,

    null,

    null,

    null,

    '{}'::jsonb,

    now(),

    now()

)

on conflict (user_id) do nothing;



insert into user_role_assignments (

    id,

    user_id,

    role_id,

    event_id,

    organization_id,

    created_at

)

select

    '33333333-3333-3333-3333-333333333333',

    '11111111-1111-1111-1111-111111111111',

    r.id,

    null,

    null,

    now()

from roles r

where r.code = 'platform_admin'

and not exists (

    select 1

    from user_role_assignments ura

    where ura.user_id = '11111111-1111-1111-1111-111111111111'

      and ura.role_id = r.id

);

-- src/main/resources/db/migration/V006__organizations_and_venues.sql
create table organizations (

    id              uuid primary key,

    type            varchar(64) not null,

    name            varchar(255) not null,

    legal_name      varchar(255),

    inn             varchar(32),

    contact_email   varchar(255),

    contact_phone   varchar(32),

    meta            jsonb not null default '{}'::jsonb,

    created_at      timestamptz not null,

    updated_at      timestamptz not null

);



create table organization_members (

    id              uuid primary key,

    organization_id uuid not null references organizations(id),

    user_id         uuid not null references users(id),

    member_role     varchar(64) not null,

    created_at      timestamptz not null

);



create index idx_organization_members_org

    on organization_members (organization_id);



create index idx_organization_members_user

    on organization_members (user_id);



create table venues (

    id           uuid primary key,

    name         varchar(255) not null,

    country_code varchar(8),

    city         varchar(128),

    address      text,

    timezone     varchar(64) not null,

    venue_meta   jsonb not null default '{}'::jsonb

);

-- src/main/resources/db/migration/V007__sports_and_regulations.sql
create table sports (

    id uuid primary key,

    code varchar(64) not null,

    name varchar(128) not null,

    is_active boolean not null default true,

    constraint uq_sports_code unique (code)

);



create table discipline_templates (

    id uuid primary key,

    sport_id uuid not null references sports(id),

    code varchar(64) not null,

    name varchar(255) not null,

    competition_format varchar(64) not null,

    unit_type varchar(64) not null,

    result_type varchar(64) not null,

    ranking_strategy varchar(64) not null,

    default_meta jsonb not null default '{}'::jsonb,

    constraint uq_discipline_templates_sport_code unique (sport_id, code)

);



create table regulation_templates (

    id uuid primary key,

    sport_id uuid not null references sports(id),

    code varchar(64) not null,

    name varchar(255) not null,

    description text,

    is_active boolean not null default true,

    created_at timestamptz not null,

    constraint uq_regulation_templates_sport_code unique (sport_id, code)

);



create table regulation_versions (

    id uuid primary key,

    regulation_template_id uuid not null references regulation_templates(id),

    version_no integer not null,

    status varchar(32) not null,

    effective_from date null,

    rules_json jsonb not null,

    notes text,

    created_by uuid null references users(id),

    created_at timestamptz not null,

    constraint uq_regulation_versions_template_version unique (regulation_template_id, version_no)

);



create index idx_regulation_versions_rules_json

    on regulation_versions using gin (rules_json);



create table category_rules (

    id uuid primary key,

    regulation_version_id uuid not null references regulation_versions(id),

    code varchar(64) not null,

    name varchar(255) not null,

    category_type varchar(64) not null,

    params_json jsonb not null,

    created_at timestamptz not null

);



create index idx_category_rules_regulation_version

    on category_rules (regulation_version_id);

-- src/main/resources/db/migration/V009__create_events.sql
create table events (

    id uuid primary key,

    organization_id uuid not null,

    venue_id uuid not null,

    sport_id uuid not null,

    regulation_version_id uuid not null,

    title varchar(255) not null,

    description text null,

    registration_open_at timestamptz null,

    registration_close_at timestamptz null,

    event_start_at timestamptz not null,

    event_end_at timestamptz not null,

    status varchar(32) not null,

    public_slug varchar(255) not null unique,

    settings_json jsonb not null default '{}'::jsonb,

    created_at timestamptz not null,

    updated_at timestamptz not null

);

-- src/main/resources/db/migration/V010__create_event_disciplines.sql
create table event_disciplines (

    id uuid primary key,



    event_id uuid not null,

    discipline_template_id uuid not null,



    code varchar(64) not null,

    name varchar(255) not null,



    competition_format varchar(64) not null,

    unit_type varchar(64) not null,

    result_type varchar(64) not null,

    ranking_strategy varchar(64) not null,



    participant_limit integer null,

    status varchar(32) not null,



    settings_json jsonb not null default '{}'::jsonb,



    created_at timestamptz not null,

    updated_at timestamptz not null,



    constraint fk_event_disciplines_event

        foreign key (event_id)

        references events (id),



    constraint fk_event_disciplines_template

        foreign key (discipline_template_id)

        references discipline_templates (id),



    constraint chk_event_disciplines_participant_limit

        check (participant_limit is null or participant_limit > 0),



    constraint uq_event_disciplines_event_code

        unique (event_id, code)

);



create index idx_event_disciplines_event_id

    on event_disciplines (event_id);



create index idx_event_disciplines_template_id

    on event_disciplines (discipline_template_id);



create index idx_event_disciplines_status

    on event_disciplines (status);

-- src/main/resources/db/migration/V011__create_registrations.sql
create table registrations (

    id uuid primary key,



    event_id uuid not null,

    event_discipline_id uuid not null,



    participant_user_id uuid not null,

    participant_profile_id uuid not null,



    status varchar(32) not null,



    participant_snapshot jsonb not null,

    registration_meta jsonb not null default '{}'::jsonb,



    review_note text null,



    submitted_at timestamptz null,

    created_at timestamptz not null,

    updated_at timestamptz not null,



    constraint fk_registrations_event

        foreign key (event_id)

        references events (id),



    constraint fk_registrations_event_discipline

        foreign key (event_discipline_id)

        references event_disciplines (id),



    constraint fk_registrations_participant_user

        foreign key (participant_user_id)

        references users (id),



    constraint fk_registrations_participant_profile

        foreign key (participant_profile_id)

        references profiles (id),



    constraint uq_registrations_discipline_participant

        unique (event_discipline_id, participant_user_id)

);



create index idx_registrations_event_id

    on registrations (event_id);



create index idx_registrations_event_discipline_id

    on registrations (event_discipline_id);



create index idx_registrations_participant_user_id

    on registrations (participant_user_id);



create index idx_registrations_status

    on registrations (status);

-- src/main/resources/db/migration/V012__add_entry_fee_to_event_disciplines.sql
alter table event_disciplines

    add column entry_fee_amount numeric(12, 2) not null default 0,

    add column entry_fee_currency varchar(3) not null default 'RUB';



alter table event_disciplines

    add constraint chk_event_disciplines_entry_fee_amount

        check (entry_fee_amount >= 0);



alter table event_disciplines

    add constraint chk_event_disciplines_entry_fee_currency

        check (

            char_length(entry_fee_currency) = 3

            and entry_fee_currency = upper(entry_fee_currency)

        );

-- src/main/resources/db/migration/V014__registrations_reviewNote.sql
ALTER TABLE registrations

    ADD COLUMN IF NOT EXISTS reviewed_by_user_id UUID NULL,

    ADD COLUMN IF NOT EXISTS reviewed_at TIMESTAMPTZ NULL;



CREATE INDEX IF NOT EXISTS idx_registrations_status ON registrations (status);

-- src/main/resources/db/migration/V015__create_competition_units.sql
CREATE TABLE competition_units (

    id UUID PRIMARY KEY,

    event_discipline_id UUID NOT NULL REFERENCES event_disciplines(id),

    label VARCHAR(100) NOT NULL,

    sequence_number INT NOT NULL,

    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',

    scheduled_at TIMESTAMPTZ,

    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()

);



CREATE INDEX idx_competition_units_event_discipline ON competition_units(event_discipline_id);



CREATE TABLE competition_unit_entries (

    id UUID PRIMARY KEY,

    competition_unit_id UUID NOT NULL REFERENCES competition_units(id),

    registration_id UUID NOT NULL REFERENCES registrations(id),

    lane_or_position INT,

    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_unit_registration UNIQUE (competition_unit_id, registration_id)

);



CREATE INDEX idx_cue_competition_unit ON competition_unit_entries(competition_unit_id);

CREATE INDEX idx_cue_registration ON competition_unit_entries(registration_id);



CREATE TABLE results (

    id UUID PRIMARY KEY,

    competition_unit_entry_id UUID NOT NULL REFERENCES competition_unit_entries(id),

    raw_value VARCHAR(50),

    result_type VARCHAR(64) NOT NULL,

    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',

    final_place INT,

    regulation_version_id UUID,

    recorded_by_user_id UUID,

    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_result_entry UNIQUE (competition_unit_entry_id)

);



CREATE INDEX idx_results_entry ON results(competition_unit_entry_id);

-- src/main/resources/db/migration/V016__add_optimistic_locking_to_results.sql
ALTER TABLE results

    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;



ALTER TABLE competition_units

    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

-- src/main/resources/db/migration/V017__add_event_owner.sql
ALTER TABLE events

    ADD COLUMN created_by_user_id UUID;



UPDATE events

SET created_by_user_id = (

    SELECT id

    FROM users

    ORDER BY created_at

    LIMIT 1

)

WHERE created_by_user_id IS NULL;



ALTER TABLE events

    ALTER COLUMN created_by_user_id SET NOT NULL;



CREATE INDEX idx_events_created_by_user_id

    ON events (created_by_user_id);