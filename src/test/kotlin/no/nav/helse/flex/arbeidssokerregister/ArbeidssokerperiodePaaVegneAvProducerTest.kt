package no.nav.helse.flex.arbeidssokerregister

import no.nav.helse.flex.FellesTestOppsett
import no.nav.paw.bekreftelse.paavegneav.v1.vo.Start
import no.nav.paw.bekreftelse.paavegneav.v1.vo.Stopp
import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.`should not be equal to`
import org.junit.jupiter.api.Test
import java.util.*

class ArbeidssokerperiodePaaVegneAvProducerTest : FellesTestOppsett() {
    @Test
    fun `Kan serialisere og sende PaaVegneAvStartMelding`() {
        val paaVegneAvMelding =
            PaaVegneAvStartMelding(
                kafkaKey = -3771L,
                periodeId = UUID.randomUUID(),
                graceMS = 86400,
            )

        paaVegneAvProducer.send(paaVegneAvMelding)

        paaVegneAvConsumer.waitForRecords(1).single().also {
            it.key() `should be equal to` paaVegneAvMelding.kafkaKey
            it.value().also {
                it.periodeId `should be equal to` paaVegneAvMelding.periodeId
                (it.handling as Start).graceMS `should be equal to` paaVegneAvMelding.graceMS
            }
        }
    }

    @Test
    fun `Kan serialisere og sende PaaVegneAvStoppMelding`() {
        val paaVegneAvMelding =
            PaaVegneAvStoppMelding(
                kafkaKey = -3771L,
                periodeId = UUID.randomUUID(),
            )

        paaVegneAvProducer.send(paaVegneAvMelding)

        paaVegneAvConsumer.waitForRecords(1).single().also {
            it.key() `should be equal to` paaVegneAvMelding.kafkaKey
            it.value().also {
                it.periodeId `should be equal to` paaVegneAvMelding.periodeId
                (it.handling as Stopp) `should not be equal to` null
            }
        }
    }
}
