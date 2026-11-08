ALTER TABLE competition_unit_entries
    ADD COLUMN category_code varchar(80),
    ADD COLUMN seeding_group_code varchar(80),
    ADD CONSTRAINT ck_competition_unit_entries_category_pair CHECK (
        (category_code IS NULL AND seeding_group_code IS NULL)
        OR (category_code IS NOT NULL AND seeding_group_code IS NOT NULL
            AND category_code ~ '^[A-Za-z0-9][A-Za-z0-9_.-]{0,79}$'
            AND seeding_group_code ~ '^[A-Za-z0-9][A-Za-z0-9_.-]{0,79}$')
    );
CREATE INDEX ix_competition_unit_entries_category ON competition_unit_entries (category_code);
