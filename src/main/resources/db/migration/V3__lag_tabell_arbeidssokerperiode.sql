CREATE TABLE arbeidssokerperiode
(
    id                VARCHAR(36) DEFAULT uuid_generate_v4() PRIMARY KEY,
    fnr               VARCHAR(11)              NOT NULL,
    vedtaksperiode_id VARCHAR                  NOT NULL UNIQUE,
    opprettet         TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX arbeidssokerperiode_vedtaksperiode_id_idx
    ON arbeidssokerperiode (vedtaksperiode_id);

CREATE INDEX arbeidssokerperiode_fnr_idx
    ON arbeidssokerperiode (fnr);

CREATE INDEX arbeidssokerperiode_opprettet_idx
    ON arbeidssokerperiode (opprettet);