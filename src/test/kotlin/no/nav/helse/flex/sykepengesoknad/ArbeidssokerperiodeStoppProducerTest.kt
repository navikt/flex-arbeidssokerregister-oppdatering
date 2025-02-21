package no.nav.helse.flex.sykepengesoknad

import no.nav.helse.flex.FellesTestOppsett
import org.amshove.kluent.`should be equal to`
import org.apache.kafka.clients.consumer.Consumer
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.util.UUID

class ArbeidssokerperiodeStoppProducerTest : FellesTestOppsett() {
    @Autowired
    private lateinit var arbeidssokerperiodeStoppProducer: ArbeidssokerperiodeStoppProducer

    @Autowired
    private lateinit var arbeidssokerperiodeStoppConsumer: Consumer<String, String>

    @BeforeAll
    fun subscribeToTopics() {
        arbeidssokerperiodeStoppConsumer.subscribeToTopics(ARBEIDSSOKERPERIODE_STOPP_TOPIC)
    }

    @Test
    fun `Sender og mottar melding om stopp som arbeidsøker`() {
        val vedtaksperiodeId = UUID.randomUUID().toString()
        val fnr = "11111111111"

        arbeidssokerperiodeStoppProducer.send(StoppMelding(vedtaksperiodeId, fnr))

        arbeidssokerperiodeStoppConsumer.waitForRecords(1).first().also {
            it.key() `should be equal to` fnr.asProducerRecordKey()

            it.value().tilArbeidssokerperiodeStoppMelding().also {
                it.vedtaksperiodeId `should be equal to` vedtaksperiodeId
                it.fnr `should be equal to` fnr
            }
        }
    }
}
