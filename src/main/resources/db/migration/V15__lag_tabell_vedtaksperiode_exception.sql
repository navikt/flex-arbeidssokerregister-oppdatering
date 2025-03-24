CREATE TABLE vedtaksperiode_exception
(
    id                   VARCHAR(36) DEFAULT uuid_generate_v4() PRIMARY KEY,
    opprettet            TIMESTAMP WITH TIME ZONE NOT NULL,
    vedtaksperiode_id    VARCHAR                  NOT NULL,
    sykepengesoknad_id   VARCHAR                  NOT NULL,
    fnr                  VARCHAR                  NOT NULL,
    exception_class_name VARCHAR                  NOT NULL,
    exception_message    VARCHAR,
    behandlet            TIMESTAMP WITH TIME ZONE
);

CREATE INDEX vedtaksperiode_exception_sykepengesoknad_id_idx ON vedtaksperiode_exception (sykepengesoknad_id);
CREATE INDEX vedtaksperiode_exception_vedtaksperiode_id_idx ON vedtaksperiode_exception (vedtaksperiode_id);
CREATE INDEX vedtaksperiode_exception_fnr_idx ON vedtaksperiode_exception (fnr);
CREATE INDEX vedtaksperiode_opprettet_behandlet_idx ON vedtaksperiode_exception (opprettet, behandlet);