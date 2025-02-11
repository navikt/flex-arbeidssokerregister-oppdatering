package no.nav.helse.flex.testdata

import no.nav.helse.flex.FellesTestOppsett
import org.amshove.kluent.`should be equal to`
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerRecord
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.util.*

class TestDataResetConsumerTest : FellesTestOppsett() {
    @Autowired
    lateinit var testProducer: Producer<String, String>

    @Autowired
    lateinit var testConsumer: Consumer<String, String>

    @BeforeAll
    fun subscribeToTopics() {
        testConsumer.subscribeToTopics(TESTDATA_RESET_TOPIC)
    }

    @Test
    fun `Melding om reset av testdata blir mottatt`() {
        val key = UUID.randomUUID().toString()
        val fnr = "11111111111"

        testProducer
            .send(
                ProducerRecord(
                    TESTDATA_RESET_TOPIC,
                    key,
                    fnr,
                ),
            ).get()

        testConsumer.waitForRecords(1).also {
            it.first().key() `should be equal to` key
            it.first().value() `should be equal to` fnr
        }
    }
}
