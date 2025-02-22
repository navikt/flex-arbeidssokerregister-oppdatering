package no.nav.helse.flex

import no.nav.helse.flex.sykepengesoknad.SYKEPENGESOKNAD_TOPIC
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.producer.Producer
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.springframework.beans.factory.annotation.Autowired

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class ArbeidssokerperiodePaaVegneAvIntegrationTest : FellesTestOppsett() {
    @Autowired
    private lateinit var arbeidssokerregisterService: ArbeidssokerregisterService

    @Autowired
    private lateinit var arbeidssokerregisterRepository: ArbeidssokerregisterRepository

    @Autowired
    private lateinit var kafkaProducer: Producer<String, String>

    @Autowired
    private lateinit var sykepengesoknadConsumer: Consumer<String, String>

    @BeforeAll
    fun subscribeToTopics() {
        sykepengesoknadConsumer.subscribeToTopics(SYKEPENGESOKNAD_TOPIC)
    }

    @Test
    @Order(1)
    fun `Søknad med status FREMTIDIG blir lagret`() {
    }
}
