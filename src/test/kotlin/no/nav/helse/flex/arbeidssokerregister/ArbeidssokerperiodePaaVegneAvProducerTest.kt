package no.nav.helse.flex.arbeidssokerregister

import no.nav.helse.flex.FellesTestOppsett
import org.amshove.kluent.`should be equal to`
import org.junit.jupiter.api.Test
import java.util.*

class ArbeidssokerperiodePaaVegneAvProducerTest : FellesTestOppsett() {
    @Test
    fun `Kan serialisere og sende PaaVegneAv`() {
        val paaVegneAvMelding =
            PaaVegneAvMelding(
                kafkaKey = -3771L,
                periodeId = UUID.randomUUID(),
                graceMS = 86400,
            )

        paaVegneAvProducer.send(paaVegneAvMelding)

        paaVegneAvConsumer.waitForRecords(1).single().also {
            it.key() `should be equal to` paaVegneAvMelding.kafkaKey
            it.value().periodeId `should be equal to` paaVegneAvMelding.periodeId
        }
    }
}
