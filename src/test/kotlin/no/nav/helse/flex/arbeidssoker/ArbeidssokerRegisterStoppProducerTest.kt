package no.nav.helse.flex.arbeidssoker

import no.nav.helse.flex.FellesTestOppsett
import org.amshove.kluent.`should be equal to`
import org.apache.kafka.clients.consumer.Consumer
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.util.*

class ArbeidssokerRegisterStoppProducerTest : FellesTestOppsett() {
    @Autowired
    private lateinit var arbeidssokerRegisterStoppProducer: ArbeidssokerRegisterStoppProducer

    @Autowired
    private lateinit var arbeidssokerRegisterStoppTestConsumer: Consumer<String, String>

    @BeforeAll
    fun subscribeToTopics() {
        arbeidssokerRegisterStoppTestConsumer.subscribeToTopics(ARBEIDSSOKERREGISTER_STOPP_TOPIC)
    }

    @Test
    fun `Sender og mottar melding om stopp som arbeidsøker`() {
        val id = UUID.randomUUID().toString()
        val fnr = "11111111111"

        arbeidssokerRegisterStoppProducer.send(ArbeidssokerRegisterStopp(id = id, fnr = fnr))

        arbeidssokerRegisterStoppTestConsumer.waitForRecords(1).also {
            it.first().key() `should be equal to` fnr.asProducerRecordKey()

            val arbeidssokerRegisterStopp = it.first().value().tilArbeidssokerRegisterStopp()
            arbeidssokerRegisterStopp.id `should be equal to` id
            arbeidssokerRegisterStopp.fnr `should be equal to` fnr
        }
    }
}
