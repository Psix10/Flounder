CREATE SCHEMA ranking_experiment;
CREATE FUNCTION ranking_experiment.numeric_value(v text)
RETURNS double precision LANGUAGE plpgsql IMMUTABLE STRICT AS $$
DECLARE m double precision; s double precision;
BEGIN
  IF v ~ '^[+-]?[0-9]+([.][0-9]+)?$' THEN
    m := v::double precision;
    RETURN CASE WHEN m = 0 THEN 0::double precision ELSE m END;
  ELSIF v ~ '^[0-9]+([.][0-9]+)?:[0-9]+([.][0-9]+)?$' THEN
    m := split_part(v, ':', 1)::double precision;
    s := split_part(v, ':', 2)::double precision;
    IF s >= 60 THEN RETURN NULL; END IF;
    RETURN m * 60::double precision + s;
  END IF;
  RETURN NULL;
END $$;
