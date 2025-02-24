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
    private lateinit var paaVegneAvProducer: ArbeidssokerperiodePaaVegneAvProducer

    @Autowired
    private lateinit var paaVegneAvConsumer: Consumer<Long, PaaVegneAv>

    @BeforeAll
    fun subscribeToTopics() {
        paaVegneAvConsumer.subscribeToTopics(ARBEIDSSOKERPERIODE_PAA_VEGNE_AV_TOPIC)
    }

    @Test
    fun `Kan serialisere og sende PaaVegneAv`() {
        val paaVegneAvMelding = PaaVegneAvMelding(-1111, UUID.randomUUID(), 86400)

        paaVegneAvProducer.send(paaVegneAvMelding)

        paaVegneAvConsumer.waitForRecords(1).first().also {
            it.key() `should be equal to` paaVegneAvMelding.kafkaKey
            it.value().periodeId `should be equal to` paaVegneAvMelding.periodeId
        }
    }
}
