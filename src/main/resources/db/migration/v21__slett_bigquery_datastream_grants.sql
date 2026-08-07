DO
$$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM pg_roles
        WHERE rolname = 'flex-arbeidssokerregister-oppdatering'
    ) THEN
        ALTER USER "flex-arbeidssokerregister-oppdatering" WITH NOREPLICATION;
END IF;
END
$$;