package no.nav.helse.flex.sykepengesoknad

import no.nav.helse.flex.FNR
import no.nav.helse.flex.FellesTestOppsett
import no.nav.helse.flex.`should be within seconds of`
import org.amshove.kluent.`should be equal to`
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class ArbeidssokerperiodeStoppProducerTest : FellesTestOppsett() {
    @Test
    fun `Sender og mottar melding om stopp som arbeidsøker`() {
        val vedtaksperiodeId = UUID.randomUUID().toString()

        arbeidssokerperiodeStoppProducer.send(StoppMelding(vedtaksperiodeId, FNR, Instant.now()))

        arbeidssokerperiodeStoppConsumer.waitForRecords(1).single().also {
            it.key() `should be equal to` FNR.asProducerRecordKey()

            it.value().tilArbeidssokerperiodeStoppMelding().also {
                it.vedtaksperiodeId `should be equal to` vedtaksperiodeId
                it.fnr `should be equal to` FNR
                it.avsluttetTidspunkt `should be within seconds of` (1 to Instant.now())
            }
        }
    }
}
