package no.nav.helse.flex.arbeidssoker

import no.nav.helse.flex.FellesTestOppsett
import org.amshove.kluent.`should be equal to`
import org.apache.kafka.clients.consumer.Consumer
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.util.*

class ArbeidssokerregisterStoppProducerTest : FellesTestOppsett() {
    @Autowired
    private lateinit var arbeidssokerregisterStoppProducer: ArbeidssokerregisterStoppProducer

    @Autowired
    private lateinit var arbeidssokerregisterStoppTestConsumer: Consumer<String, String>

    @BeforeAll
    fun subscribeToTopics() {
        arbeidssokerregisterStoppTestConsumer.subscribeToTopics(ARBEIDSSOKERREGISTER_STOPP_TOPIC)
    }

    @Test
    fun `Sender og mottar melding om stopp som arbeidsøker`() {
        val id = UUID.randomUUID().toString()
        val fnr = "11111111111"

        arbeidssokerregisterStoppProducer.send(ArbeidssokerregisterStoppMelding(id = id, fnr = fnr))

        arbeidssokerregisterStoppTestConsumer.waitForRecords(1).also {
            it.first().key() `should be equal to` fnr.asProducerRecordKey()

            val arbeidssokerregisterStoppMelding = it.first().value().tilArbeidssokerregisterStoppMelding()
            arbeidssokerregisterStoppMelding.id `should be equal to` id
            arbeidssokerregisterStoppMelding.fnr `should be equal to` fnr
        }
    }
}
