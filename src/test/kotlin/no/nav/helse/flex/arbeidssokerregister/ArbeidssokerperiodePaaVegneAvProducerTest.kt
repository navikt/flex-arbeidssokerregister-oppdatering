package no.nav.helse.flex.arbeidssokerregister

import no.nav.helse.flex.FellesTestOppsett
import no.nav.paw.bekreftelse.paavegneav.v1.PaaVegneAv
import org.amshove.kluent.`should be equal to`
import org.apache.kafka.clients.consumer.Consumer
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.util.*

class ArbeidssokerperiodePaaVegneAvProducerTest : FellesTestOppsett() {
    @Autowired
    private lateinit var arbeidssokerperiodePaaVegneAvProducer: ArbeidssokerperiodePaaVegneAvProducer

    @Autowired
    private lateinit var paaVegneAvTestConsumer: Consumer<Long, PaaVegneAv>

    @BeforeAll
    fun subscribeToTopics() {
        paaVegneAvTestConsumer.subscribeToTopics(ARBEIDSSOKERPERIODE_PAA_VEGNE_AV_TOPIC)
    }

    @Test
    fun `Kan serialisere og sende PaaVegneAv`() {
        val paaVegneAvMelding = PaaVegneAvMelding(-1111, UUID.randomUUID())

        arbeidssokerperiodePaaVegneAvProducer.send(paaVegneAvMelding)

        paaVegneAvTestConsumer.waitForRecords(1).first().also {
            it.key() `should be equal to` paaVegneAvMelding.kafkaKey
            it.value().periodeId `should be equal to` paaVegneAvMelding.periodeId
        }
    }
}
