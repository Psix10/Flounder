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