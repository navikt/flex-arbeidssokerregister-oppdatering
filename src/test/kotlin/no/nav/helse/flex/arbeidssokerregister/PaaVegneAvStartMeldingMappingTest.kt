package no.nav.helse.flex.arbeidssokerregister

import io.kotest.matchers.shouldBe
import no.nav.helse.flex.FNR
import no.nav.helse.flex.VEDTAKSPERIODE_ID
import no.nav.helse.flex.arbeidssokerperiode.Arbeidssokerperiode
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import java.time.LocalDate

class PaaVegneAvStartMeldingMappingTest {
    @Test
    fun `Startmelding uten lagret arbeidssøkerperiodeId feiler`() {
        val periode = lagArbeidssokerperiode().copy(id = null)

        val exception =
            assertThrows<IllegalStateException> {
                periode.tilPaaVegneAvStartMelding(0L)
            }

        exception.message shouldBe "Mangler arbeidssøkerperiodeId for vedtaksperiode: $VEDTAKSPERIODE_ID."
    }

    @Test
    fun `Startmelding uten Kafka-key feiler`() {
        val periode = lagArbeidssokerperiode().copy(kafkaRecordKey = null)

        val exception =
            assertThrows<IllegalStateException> {
                periode.tilPaaVegneAvStartMelding(0L)
            }

        exception.message shouldBe
            "Mangler Kafka-key for arbeidssøkerperiode: ${periode.id} med vedtaksperiode: $VEDTAKSPERIODE_ID."
    }

    @Test
    fun `Startmelding uten periodeId i arbeidssøkerregisteret feiler`() {
        val periode = lagArbeidssokerperiode().copy(arbeidssokerperiodeId = null)

        val exception =
            assertThrows<IllegalStateException> {
                periode.tilPaaVegneAvStartMelding(0L)
            }

        exception.message shouldBe
            "Mangler periodeId i arbeidssøkerregisteret for arbeidssøkerperiode: ${periode.id} med vedtaksperiode: $VEDTAKSPERIODE_ID."
    }

    private fun lagArbeidssokerperiode() =
        Arbeidssokerperiode(
            id = "arbeidssokerperiode-id",
            fnr = FNR,
            vedtaksperiodeId = VEDTAKSPERIODE_ID,
            vedtaksperiodeFom = LocalDate.of(2025, 1, 1),
            vedtaksperiodeTom = LocalDate.of(2025, 1, 31),
            opprettet = Instant.parse("2025-01-01T00:00:00Z"),
            kafkaRecordKey = 1000L,
            arbeidssokerperiodeId = "registerperiode-id",
        )
}
