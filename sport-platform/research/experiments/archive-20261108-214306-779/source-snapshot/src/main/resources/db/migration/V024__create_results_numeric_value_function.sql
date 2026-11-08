CREATE FUNCTION results_numeric_value(raw TEXT)
RETURNS DOUBLE PRECISION
LANGUAGE plpgsql
IMMUTABLE
STRICT
AS $$
DECLARE
    minutes_value DOUBLE PRECISION;
    seconds_value DOUBLE PRECISION;
    parsed_value DOUBLE PRECISION;
BEGIN
    IF length(raw) > 50 THEN
        RETURN NULL;
    END IF;

    IF raw ~ '^[+-]?[0-9]+([.][0-9]+)?$' THEN
        parsed_value := raw::DOUBLE PRECISION;

        IF parsed_value = 0 THEN
            RETURN 0::DOUBLE PRECISION;
        END IF;

        RETURN parsed_value;
    END IF;

    IF raw !~ '^[0-9]+:[0-9]{1,2}([.][0-9]+)?$' THEN
        RETURN NULL;
    END IF;

    minutes_value := split_part(raw, ':', 1)::DOUBLE PRECISION;
    seconds_value := split_part(raw, ':', 2)::DOUBLE PRECISION;

    IF seconds_value >= 60 THEN
        RETURN NULL;
    END IF;

    RETURN minutes_value * 60 + seconds_value;
END;
$$;