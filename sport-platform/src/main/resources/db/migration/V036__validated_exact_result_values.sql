-- Keep historical parsers and stored snapshots unchanged. Use the typed exact
-- parser for future ranking and reject non-positive TIME values.
CREATE FUNCTION results_validated_numeric_value(raw text, result_type text)
RETURNS numeric
LANGUAGE plpgsql
IMMUTABLE
STRICT
AS $$
DECLARE
    n numeric;
BEGIN
    n := results_standings_exact_value(raw, result_type);
    IF n IS NULL THEN
        RETURN NULL;
    END IF;
    IF upper(btrim(result_type)) = 'TIME' AND n <= 0 THEN
        RETURN NULL;
    END IF;
    RETURN n;
END;
$$;
