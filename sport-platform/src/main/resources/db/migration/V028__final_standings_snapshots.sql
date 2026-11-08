CREATE TABLE final_standings_snapshots (
    id uuid PRIMARY KEY,
    event_id uuid NOT NULL REFERENCES events(id),
    event_discipline_id uuid NOT NULL REFERENCES event_disciplines(id),
    request_id uuid NOT NULL,
    revision_no integer NOT NULL CHECK (revision_no > 0),
    status varchar(16) NOT NULL CHECK (status IN ('CONFIRMED','REVOKED')),
    confirmed_by uuid NOT NULL REFERENCES users(id),
    confirmed_at timestamptz NOT NULL,
    confirmation_note varchar(500) NOT NULL DEFAULT '',
    standings_json jsonb NOT NULL CHECK (jsonb_typeof(standings_json)='object'),
    source_json jsonb NOT NULL CHECK (jsonb_typeof(source_json)='object'),
    revoked_by uuid REFERENCES users(id),
    revoked_at timestamptz,
    revocation_reason varchar(1000),
    CONSTRAINT uq_final_standings_revision UNIQUE(event_discipline_id,revision_no),
    CONSTRAINT uq_final_standings_request UNIQUE(event_discipline_id,request_id),
    CONSTRAINT ck_final_standings_revocation CHECK (
        (status='CONFIRMED' AND revoked_by IS NULL AND revoked_at IS NULL AND revocation_reason IS NULL)
        OR (status='REVOKED' AND revoked_by IS NOT NULL AND revoked_at IS NOT NULL
            AND revocation_reason IS NOT NULL AND length(btrim(revocation_reason)) > 0)
    )
);
CREATE UNIQUE INDEX uq_final_standings_active ON final_standings_snapshots(event_discipline_id) WHERE status='CONFIRMED';

CREATE FUNCTION results_finalization_source(discipline uuid) RETURNS jsonb
LANGUAGE sql STABLE STRICT AS $$
    SELECT jsonb_build_object('schemaVersion',1,
        'units',COALESCE((SELECT jsonb_agg(to_jsonb(u) ORDER BY u.sequence_number,u.id)
                         FROM competition_units u WHERE u.event_discipline_id=discipline),'[]'::jsonb),
        'entries',COALESCE((SELECT jsonb_agg(to_jsonb(e) || jsonb_build_object('result',to_jsonb(r)) ORDER BY e.id)
                           FROM competition_unit_entries e JOIN competition_units u ON u.id=e.competition_unit_id
                           LEFT JOIN results r ON r.competition_unit_entry_id=e.id
                           WHERE u.event_discipline_id=discipline),'[]'::jsonb));
$$;

CREATE FUNCTION results_finalization_key(discipline uuid) RETURNS bigint
LANGUAGE sql IMMUTABLE STRICT AS $$
    SELECT hashtextextended('sport-platform:final-standings:' || discipline::text,0);
$$;

CREATE FUNCTION results_finalization_lock(discipline uuid) RETURNS void
LANGUAGE plpgsql VOLATILE STRICT AS $$
BEGIN
    IF current_setting('transaction_isolation') <> 'read committed' THEN
        RAISE EXCEPTION USING ERRCODE='PZ003', MESSAGE='results.final_isolation_not_supported';
    END IF;
    PERFORM pg_advisory_xact_lock(results_finalization_key(discipline));
END;
$$;

CREATE FUNCTION results_finalization_write_guard(discipline uuid) RETURNS void
LANGUAGE plpgsql VOLATILE STRICT AS $$
BEGIN
    IF current_setting('transaction_isolation') <> 'read committed' THEN
        RAISE EXCEPTION USING ERRCODE='PZ003', MESSAGE='results.final_isolation_not_supported';
    END IF;
    PERFORM pg_advisory_xact_lock_shared(results_finalization_key(discipline));
    IF EXISTS (SELECT 1 FROM final_standings_snapshots WHERE event_discipline_id=discipline AND status='CONFIRMED') THEN
        RAISE EXCEPTION USING ERRCODE='PZ001', MESSAGE='results.discipline_finalized';
    END IF;
END;
$$;

CREATE FUNCTION guard_finalized_results_rows() RETURNS trigger
LANGUAGE plpgsql VOLATILE AS $$
DECLARE
    before_row jsonb;
    after_row jsonb;
    before_id uuid;
    after_id uuid;
    discipline uuid;
BEGIN
    IF TG_OP <> 'INSERT' THEN before_row=to_jsonb(OLD); END IF;
    IF TG_OP <> 'DELETE' THEN after_row=to_jsonb(NEW); END IF;
    IF TG_TABLE_NAME='competition_units' THEN
        before_id=(before_row->>'event_discipline_id')::uuid;
        after_id=(after_row->>'event_discipline_id')::uuid;
        FOR discipline IN SELECT DISTINCT v FROM unnest(ARRAY[before_id,after_id]) AS x(v) WHERE v IS NOT NULL ORDER BY v LOOP
            PERFORM results_finalization_write_guard(discipline);
        END LOOP;
    ELSIF TG_TABLE_NAME='competition_unit_entries' THEN
        before_id=(before_row->>'competition_unit_id')::uuid;
        after_id=(after_row->>'competition_unit_id')::uuid;
        FOR discipline IN SELECT DISTINCT event_discipline_id FROM competition_units
                WHERE id IN (before_id,after_id) ORDER BY event_discipline_id LOOP
            PERFORM results_finalization_write_guard(discipline);
        END LOOP;
    ELSIF TG_TABLE_NAME='results' THEN
        before_id=(before_row->>'competition_unit_entry_id')::uuid;
        after_id=(after_row->>'competition_unit_entry_id')::uuid;
        FOR discipline IN SELECT DISTINCT u.event_discipline_id FROM competition_units u
                JOIN competition_unit_entries e ON e.competition_unit_id=u.id
                WHERE e.id IN (before_id,after_id) ORDER BY u.event_discipline_id LOOP
            PERFORM results_finalization_write_guard(discipline);
        END LOOP;
    ELSIF TG_TABLE_NAME='registrations' THEN
        IF before_row->>'status'='CONFIRMED' OR after_row->>'status'='CONFIRMED' THEN
            before_id=(before_row->>'event_discipline_id')::uuid;
            after_id=(after_row->>'event_discipline_id')::uuid;
            FOR discipline IN SELECT DISTINCT v FROM unnest(ARRAY[before_id,after_id]) AS x(v) WHERE v IS NOT NULL ORDER BY v LOOP
                PERFORM results_finalization_write_guard(discipline);
            END LOOP;
        END IF;
    END IF;
    IF TG_OP='DELETE' THEN RETURN OLD; END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER guard_final_competition_units BEFORE INSERT OR UPDATE OR DELETE ON competition_units
    FOR EACH ROW EXECUTE FUNCTION guard_finalized_results_rows();
CREATE TRIGGER guard_final_competition_entries BEFORE INSERT OR UPDATE OR DELETE ON competition_unit_entries
    FOR EACH ROW EXECUTE FUNCTION guard_finalized_results_rows();
CREATE TRIGGER guard_final_result_values BEFORE INSERT OR UPDATE OR DELETE ON results
    FOR EACH ROW EXECUTE FUNCTION guard_finalized_results_rows();
CREATE TRIGGER guard_final_confirmed_registrations BEFORE INSERT OR UPDATE OR DELETE ON registrations
    FOR EACH ROW EXECUTE FUNCTION guard_finalized_results_rows();

CREATE FUNCTION guard_finalized_discipline_policy() RETURNS trigger
LANGUAGE plpgsql VOLATILE AS $$
BEGIN
    IF TG_OP='DELETE' THEN
        PERFORM results_finalization_write_guard(OLD.id);
        RETURN OLD;
    END IF;
    IF (NEW.result_type,NEW.ranking_strategy,NEW.event_id) IS DISTINCT FROM (OLD.result_type,OLD.ranking_strategy,OLD.event_id) THEN
        PERFORM results_finalization_write_guard(OLD.id);
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER guard_final_discipline_policy BEFORE UPDATE OR DELETE ON event_disciplines
    FOR EACH ROW EXECUTE FUNCTION guard_finalized_discipline_policy();

CREATE FUNCTION guard_final_standings_history() RETURNS trigger
LANGUAGE plpgsql VOLATILE AS $$
BEGIN
    IF TG_OP='TRUNCATE' OR TG_OP='DELETE' THEN
        RAISE EXCEPTION USING ERRCODE='PZ002', MESSAGE='results.final_history_immutable';
    END IF;
    IF TG_OP='INSERT' THEN
        PERFORM results_finalization_lock(NEW.event_discipline_id);
        IF NEW.status <> 'CONFIRMED' OR NOT EXISTS (SELECT 1 FROM event_disciplines d
                WHERE d.id=NEW.event_discipline_id AND d.event_id=NEW.event_id) THEN
            RAISE EXCEPTION USING ERRCODE='PZ002', MESSAGE='results.final_history_immutable';
        END IF;
        RETURN NEW;
    END IF;
    PERFORM results_finalization_lock(OLD.event_discipline_id);
    IF OLD.status <> 'CONFIRMED' OR NEW.status <> 'REVOKED'
        OR (to_jsonb(NEW)-ARRAY['status','revoked_by','revoked_at','revocation_reason'])
            IS DISTINCT FROM (to_jsonb(OLD)-ARRAY['status','revoked_by','revoked_at','revocation_reason']) THEN
        RAISE EXCEPTION USING ERRCODE='PZ002', MESSAGE='results.final_history_immutable';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER guard_final_standings_rows BEFORE INSERT OR UPDATE OR DELETE ON final_standings_snapshots
    FOR EACH ROW EXECUTE FUNCTION guard_final_standings_history();
CREATE TRIGGER guard_final_standings_truncate BEFORE TRUNCATE ON final_standings_snapshots
    FOR EACH STATEMENT EXECUTE FUNCTION guard_final_standings_history();

CREATE FUNCTION guard_finalized_results_truncate() RETURNS trigger
LANGUAGE plpgsql VOLATILE AS $$
BEGIN
    IF EXISTS (SELECT 1 FROM final_standings_snapshots WHERE status='CONFIRMED') THEN
        RAISE EXCEPTION USING ERRCODE='PZ001', MESSAGE='results.discipline_finalized';
    END IF;
    RETURN NULL;
END;
$$;
CREATE TRIGGER guard_final_units_truncate BEFORE TRUNCATE ON competition_units
    FOR EACH STATEMENT EXECUTE FUNCTION guard_finalized_results_truncate();
CREATE TRIGGER guard_final_entries_truncate BEFORE TRUNCATE ON competition_unit_entries
    FOR EACH STATEMENT EXECUTE FUNCTION guard_finalized_results_truncate();
CREATE TRIGGER guard_final_values_truncate BEFORE TRUNCATE ON results
    FOR EACH STATEMENT EXECUTE FUNCTION guard_finalized_results_truncate();
CREATE TRIGGER guard_final_registrations_truncate BEFORE TRUNCATE ON registrations
    FOR EACH STATEMENT EXECUTE FUNCTION guard_finalized_results_truncate();
