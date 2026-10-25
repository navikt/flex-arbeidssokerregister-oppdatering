package no.nav.helse.flex.sykepengesoknad

import io.kotest.matchers.shouldBe
import no.nav.helse.flex.FNR
import no.nav.helse.flex.FellesTestOppsett
import no.nav.helse.flex.`should be within seconds of`
import no.nav.helse.flex.sykepengesoknad.StartStop.STOPP
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class ArbeidssokerperiodeStartStoppProducerTest : FellesTestOppsett() {
    @Test
    fun `Sender og mottar melding om stopp som arbeidsøker på start-stop-topic`() {
        val vedtaksperiodeId = UUID.randomUUID().toString()

        arbeidssokerperiodeStartStoppProducer.send(StartStoppMelding(STOPP, vedtaksperiodeId, FNR, Instant.now()))

        arbeidssokerperiodeStartStoppConsumer.waitForRecords(1).single().also { startStoppMelding ->
            startStoppMelding.key() shouldBe FNR.asProducerRecordKey()

            startStoppMelding.value().tilArbeidssokerperiodeStartStoppMelding().also {
                it.operation shouldBe STOPP
                it.vedtaksperiodeId shouldBe vedtaksperiodeId
                it.fnr shouldBe FNR
                it.tidspunkt `should be within seconds of` (1 to Instant.now())
            }
        }
    }
}
