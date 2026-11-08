CREATE FUNCTION results_standings_exact_value(raw text, result_type text)
RETURNS numeric
LANGUAGE plpgsql
IMMUTABLE
STRICT
AS $$
DECLARE
    v text := btrim(raw);
    number_pattern constant text := '^[+-]?([0-9]+([.][0-9]*)?|[.][0-9]+)([eE][+-]?[0-9]+)?$';
    parts text[];
    n numeric;
    seconds_value numeric;
BEGIN
    IF v = '' OR length(raw) > 50 THEN
        RETURN NULL;
    END IF;

    IF upper(btrim(result_type)) <> 'TIME' THEN
        IF v !~ number_pattern THEN
            RETURN NULL;
        END IF;

        RETURN v::numeric;
    END IF;

    IF position(':' IN v) = 0 THEN
        IF v !~ number_pattern THEN
            RETURN NULL;
        END IF;

        n := v::numeric;

        IF n < 0 THEN
            RETURN NULL;
        END IF;

        RETURN n;
    END IF;

    parts := string_to_array(v, ':');

    IF cardinality(parts) <> 2
            OR parts[1] !~ '^[0-9]+$'
            OR parts[2] !~ number_pattern THEN
        RETURN NULL;
    END IF;

    seconds_value := parts[2]::numeric;

    IF seconds_value < 0 OR seconds_value >= 60 THEN
        RETURN NULL;
    END IF;

    RETURN parts[1]::numeric * 60 + seconds_value;
EXCEPTION
    WHEN numeric_value_out_of_range OR invalid_text_representation THEN
        RETURN NULL;
END;
$$;