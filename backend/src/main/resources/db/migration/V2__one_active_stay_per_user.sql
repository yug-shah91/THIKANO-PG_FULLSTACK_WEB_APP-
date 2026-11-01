-- One person can have many stays over time (one tenants row per stay), but only ONE active stay.
-- Flyway baselines an existing database at version 1 (the schema Hibernate created), then runs this.

-- 1) the tenants table must exist (it is created by the first application start with FLYWAY_ENABLED=false)
DO $$
BEGIN
    IF to_regclass('tenants') IS NULL THEN
        RAISE EXCEPTION 'The tenants table does not exist. Start the app once with FLYWAY_ENABLED=false (creates the tables), then enable Flyway.';
    END IF;
END $$;

-- 2) drop the old unique(user_id) constraint (from when a user could only ever have one tenants row)
DO $$
DECLARE
    old_name text;
BEGIN
    FOR old_name IN
        SELECT con.conname
        FROM pg_constraint con
        JOIN pg_class rel ON rel.oid = con.conrelid
        JOIN pg_attribute att ON att.attrelid = rel.oid AND att.attnum = ANY (con.conkey)
        WHERE rel.relname = 'tenants' AND con.contype = 'u'
          AND att.attname = 'user_id' AND array_length(con.conkey, 1) = 1
    LOOP
        EXECUTE format('ALTER TABLE tenants DROP CONSTRAINT %I', old_name);
    END LOOP;
END $$;

-- 3) refuse to continue if the data already breaks the rule (fix the data, then run again)
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM tenants WHERE is_active = true GROUP BY user_id HAVING COUNT(*) > 1) THEN
        RAISE EXCEPTION 'Some users have more than one ACTIVE stay in tenants. Set is_active = false on the extra rows, then restart.';
    END IF;
END $$;

-- 4) the rule itself
CREATE UNIQUE INDEX IF NOT EXISTS uq_tenant_one_active_stay ON tenants (user_id) WHERE is_active = true;
