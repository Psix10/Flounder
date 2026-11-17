DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM organizations
        WHERE NULLIF(btrim(inn), '') IS NOT NULL
        GROUP BY btrim(inn)
        HAVING count(*) > 1
    ) THEN
        RAISE EXCEPTION
            'Duplicate non-empty organization INNs exist. Reconcile organizations before applying this migration.';
    END IF;
END
$$;

CREATE UNIQUE INDEX uq_organizations_inn_normalized
    ON organizations (btrim(inn))
    WHERE NULLIF(btrim(inn), '') IS NOT NULL;