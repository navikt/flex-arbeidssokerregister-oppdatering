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
                arbeidssokerperiodeId = UUID.randomUUID().toString(),
                arbeidssokerregisterPeriodeId = UUID.randomUUID().toString(),
                graceMS = 86400,
            )

        paaVegneAvProducer.send(paaVegneAvMelding)

        paaVegneAvConsumer.waitForRecords(1).single().also {
            it.key() `should be equal to` paaVegneAvMelding.kafkaKey
            it.value().also {
                it.periodeId `should be equal to` UUID.fromString(paaVegneAvMelding.arbeidssokerregisterPeriodeId)
                (it.handling as Start).graceMS `should be equal to` paaVegneAvMelding.graceMS
            }
        }
    }

    @Test
    fun `Kan serialisere og sende PaaVegneAvStoppMelding`() {
        val paaVegneAvMelding =
            PaaVegneAvStoppMelding(
                kafkaKey = -3771L,
                arbeidssokerperiodeId = UUID.randomUUID().toString(),
                arbeidssokerregisterPeriodeId = UUID.randomUUID().toString(),
            )

        paaVegneAvProducer.send(paaVegneAvMelding)

        paaVegneAvConsumer.waitForRecords(1).single().also {
            it.key() `should be equal to` paaVegneAvMelding.kafkaKey
            it.value().also {
                it.periodeId `should be equal to` UUID.fromString(paaVegneAvMelding.arbeidssokerregisterPeriodeId)
                (it.handling as Stopp) `should not be equal to` null
            }
        }
    }
}
