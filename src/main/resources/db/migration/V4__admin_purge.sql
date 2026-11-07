-- Historical tables stay protected against accidental change, but an admin data-cleanup
-- (which sets outpass.allow_purge=on inside its own transaction) may delete / detach rows.
CREATE OR REPLACE FUNCTION forbid_change() RETURNS trigger AS $$
BEGIN
    IF current_setting('outpass.allow_purge', true) = 'on' THEN
        IF TG_OP = 'DELETE' THEN RETURN OLD; ELSE RETURN NEW; END IF;
    END IF;
    RAISE EXCEPTION 'Table % is append-only (use Admin > Data cleanup)', TG_TABLE_NAME;
END;
$$ LANGUAGE plpgsql;
