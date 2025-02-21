package no.nav.helse.flex.testdata

import no.nav.helse.flex.FellesTestOppsett
import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.`should not be`
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerRecord
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.util.*
import java.util.concurrent.TimeUnit

class TestdataResetConsumerTest : FellesTestOppsett() {
    @Autowired
    private lateinit var testdataResetListener: TestdataResetListener

    @Autowired
    private lateinit var kafkaProducer: Producer<String, String>

    @Autowired
    private lateinit var testdataResetConsumer: Consumer<String, String>

    @BeforeAll
    fun subscribeToTopics() {
        testdataResetConsumer.subscribeToTopics(TESTDATA_RESET_TOPIC)
    }

    @Test
    fun `Mottat melding om testdata reset`() {
        val key = UUID.randomUUID().toString()
        val fnr = "11111111111"

        kafkaProducer.send(ProducerRecord(TESTDATA_RESET_TOPIC, key, fnr)).get()

        await().atMost(1, TimeUnit.SECONDS).untilAsserted {
            testdataResetListener.hentMelding(key) `should not be` null
        }

        testdataResetConsumer.waitForRecords(1).also {
            it.first().key() `should be equal to` key
            it.first().value() `should be equal to` fnr
        }
    }
}
