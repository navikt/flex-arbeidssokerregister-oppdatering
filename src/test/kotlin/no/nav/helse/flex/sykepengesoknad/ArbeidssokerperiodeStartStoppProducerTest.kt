package no.nav.helse.flex.sykepengesoknad

import no.nav.helse.flex.FNR
import no.nav.helse.flex.FellesTestOppsett
import no.nav.helse.flex.`should be within seconds of`
import no.nav.helse.flex.sykepengesoknad.StartStop.STOPP
import org.amshove.kluent.`should be equal to`
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class ArbeidssokerperiodeStartStoppProducerTest : FellesTestOppsett() {
    @Test
    fun `Sender og mottar melding om stopp som arbeidsøker på gammelt topic`() {
        val vedtaksperiodeId = UUID.randomUUID().toString()

        arbeidssokerperiodeStoppProducer.send(StoppMelding(vedtaksperiodeId, FNR, Instant.now()))

        arbeidssokerperiodeStoppConsumer.waitForRecords(1).single().also { stoppMelding ->
            stoppMelding.key() `should be equal to` FNR.asProducerRecordKey()

            stoppMelding.value().tilArbeidssokerperiodeStoppMelding().also {
                it.vedtaksperiodeId `should be equal to` vedtaksperiodeId
                it.fnr `should be equal to` FNR
                it.avsluttetTidspunkt `should be within seconds of` (1 to Instant.now())
            }
        }
    }

    @Test
    fun `Sender og mottar melding om stopp som arbeidsøker på start-stop-topic`() {
        val vedtaksperiodeId = UUID.randomUUID().toString()

        arbeidssokerperiodeStartStoppProducer.send(StartStoppMelding(STOPP, vedtaksperiodeId, FNR, Instant.now()))

        arbeidssokerperiodeStartStoppConsumer.waitForRecords(1).single().also { startStoppMelding ->
            startStoppMelding.key() `should be equal to` FNR.asProducerRecordKey()

            startStoppMelding.value().tilArbeidssokerperiodeStartStoppMelding().also {
                it.operation `should be equal to` STOPP
                it.vedtaksperiodeId `should be equal to` vedtaksperiodeId
                it.fnr `should be equal to` FNR
                it.tidspunkt `should be within seconds of` (1 to Instant.now())
            }
        }
    }
}
