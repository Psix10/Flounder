-- Isolated database only. Parent tables are ID-only FK stubs, not the full application schema.
CREATE TABLE event_disciplines(id uuid PRIMARY KEY)@@
CREATE TABLE registrations(id uuid PRIMARY KEY)@@
CREATE TABLE competition_units(
 id uuid PRIMARY KEY,event_discipline_id uuid NOT NULL REFERENCES event_disciplines(id),
 label varchar(100) NOT NULL,sequence_number integer NOT NULL,status varchar(32) NOT NULL DEFAULT 'DRAFT',
 scheduled_at timestamptz,created_at timestamptz NOT NULL DEFAULT now(),updated_at timestamptz NOT NULL DEFAULT now(),version bigint NOT NULL DEFAULT 0)@@
CREATE INDEX idx_competition_units_event_discipline ON competition_units(event_discipline_id)@@
CREATE TABLE competition_unit_entries(
 id uuid PRIMARY KEY,competition_unit_id uuid NOT NULL REFERENCES competition_units(id),
 registration_id uuid NOT NULL REFERENCES registrations(id),lane_or_position integer,
 created_at timestamptz NOT NULL DEFAULT now(),CONSTRAINT uq_unit_registration UNIQUE(competition_unit_id,registration_id))@@
CREATE INDEX idx_cue_competition_unit ON competition_unit_entries(competition_unit_id)@@
CREATE INDEX idx_cue_registration ON competition_unit_entries(registration_id)@@
CREATE TABLE results(
 id uuid PRIMARY KEY,competition_unit_entry_id uuid NOT NULL REFERENCES competition_unit_entries(id),raw_value varchar(50),
 result_type varchar(64) NOT NULL,status varchar(32) NOT NULL DEFAULT 'PENDING',final_place integer,
 regulation_version_id uuid,recorded_by_user_id uuid,created_at timestamptz NOT NULL DEFAULT now(),
 updated_at timestamptz NOT NULL DEFAULT now(),version bigint NOT NULL DEFAULT 0,
 CONSTRAINT uq_result_entry UNIQUE(competition_unit_entry_id))@@
CREATE INDEX idx_results_entry ON results(competition_unit_entry_id)@@
CREATE FUNCTION experiment_numeric(raw text) RETURNS double precision LANGUAGE plpgsql IMMUTABLE STRICT AS $$
DECLARE m double precision; s double precision;
BEGIN
 IF length(raw)>50 THEN RETURN NULL; END IF;
 IF raw ~ '^[+-]?[0-9]+([.][0-9]+)?$' THEN
  s:=raw::double precision;
  IF s=0 THEN RETURN 0::double precision; END IF;
  RETURN s;
 END IF;
 IF raw !~ '^[0-9]+:[0-9]{1,2}([.][0-9]+)?$' THEN RETURN NULL; END IF;
 m:=split_part(raw,':',1)::double precision; s:=split_part(raw,':',2)::double precision;
 IF s>=60 THEN RETURN NULL; END IF;
 RETURN m*60+s;
END;
$$@@
