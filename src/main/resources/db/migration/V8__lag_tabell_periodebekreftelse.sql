CREATE TABLE periodebekreftelse
(
    id                     VARCHAR DEFAULT uuid_generate_v4() PRIMARY KEY,
    arbeidssokerperiode_id VARCHAR                  NOT NULL REFERENCES arbeidssokerperiode (id),
    sykepengesoknad_id     VARCHAR                  NOT NULL,
    fortsatt_arbeidssoker  BOOLEAN                  NOT NULL,
    inntekt_underveis      BOOLEAN                  NOT NULL,
    opprettet              TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX periodebekreftelse_vedtaksperiode_id_idx
    ON periodebekreftelse (arbeidssokerperiode_id);

CREATE INDEX periodebekreftelse_sykepengesoknad_id_idx
    ON periodebekreftelse (sykepengesoknad_id);

CREATE INDEX periodebekreftelse_opprettet_id_idx
    ON periodebekreftelse (opprettet);

CREATE INDEX periodebekreftelse_bekreftelse_id_idx
    ON periodebekreftelse (fortsatt_arbeidssoker, inntekt_underveis);

