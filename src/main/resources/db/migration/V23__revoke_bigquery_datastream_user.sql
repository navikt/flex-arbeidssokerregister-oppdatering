DO
$$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM pg_roles
        WHERE rolname = 'bigquery-datastream'
    )
    THEN
        ALTER USER "bigquery-datastream" WITH NOREPLICATION;

        ALTER DEFAULT PRIVILEGES IN SCHEMA public
            REVOKE SELECT ON TABLES FROM "bigquery-datastream";

        REVOKE USAGE ON SCHEMA public FROM "bigquery-datastream";
        REVOKE SELECT ON ALL TABLES IN SCHEMA public FROM "bigquery-datastream";
    END IF;
END
$$;