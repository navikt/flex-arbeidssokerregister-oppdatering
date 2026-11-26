package no.nav.helse.flex.api

import io.kotest.matchers.shouldBe
import no.nav.helse.flex.FNR
import no.nav.helse.flex.VEDTAKSPERIODE_ID
import no.nav.helse.flex.arbeidssokerperiode.Arbeidssokerperiode
import no.nav.helse.flex.sykepengesoknad.Periodebekreftelse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import java.time.LocalDate

class FlexInternalResponseMappingTest {
    @Test
    fun `Arbeidssøkerperioderespons uten lagret id feiler`() {
        val periode =
            Arbeidssokerperiode(
                fnr = FNR,
                vedtaksperiodeId = VEDTAKSPERIODE_ID,
                vedtaksperiodeFom = LocalDate.of(2025, 1, 1),
                vedtaksperiodeTom = LocalDate.of(2025, 1, 31),
                opprettet = Instant.parse("2025-01-01T00:00:00Z"),
            )

        val exception =
            assertThrows<IllegalStateException> {
                periode.tilArbeidssokerperiodeResponse(emptyList())
            }

        exception.message shouldBe "Mangler arbeidssøkerperiodeId for vedtaksperiode: $VEDTAKSPERIODE_ID."
    }

    @Test
    fun `Periodebekreftelserespons uten lagret id feiler`() {
        val bekreftelse =
            Periodebekreftelse(
                arbeidssokerperiodeId = "arbeidssokerperiode-id",
                sykepengesoknadId = "soknad-id",
                fortsattArbeidssoker = true,
                inntektUnderveis = false,
                opprettet = Instant.parse("2025-01-01T00:00:00Z"),
            )

        val exception =
            assertThrows<IllegalStateException> {
                bekreftelse.tilPeriodebekreftelseResponse()
            }

        exception.message shouldBe
            "Mangler periodebekreftelseId for søknad: ${bekreftelse.sykepengesoknadId} med " +
            "arbeidssøkerperiode: ${bekreftelse.arbeidssokerperiodeId}."
    }
}
