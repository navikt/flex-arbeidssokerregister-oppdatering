package no.nav.helse.flex.testdata

import no.nav.helse.flex.Arbeidssokerperiode
import no.nav.helse.flex.ArbeidssokerperiodeRepository
import no.nav.helse.flex.FNR
import no.nav.helse.flex.FellesTestOppsett
import org.amshove.kluent.`should be equal to`
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerRecord
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.springframework.beans.factory.annotation.Autowired
import java.time.Instant
import java.util.*

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class TestdataResetConsumerTest : FellesTestOppsett() {
    @Autowired
    private lateinit var arbeidssokerperiodeRepository: ArbeidssokerperiodeRepository

    @Autowired
    private lateinit var kafkaProducer: Producer<String, String>

    @Autowired
    private lateinit var testdataResetConsumer: Consumer<String, String>

    @BeforeAll
    fun subscribeToTopics() {
        testdataResetConsumer.subscribeToTopics(TESTDATA_RESET_TOPIC)
    }

    @BeforeAll
    fun slettFraDatabase() {
        arbeidssokerperiodeRepository.deleteAll()
    }

    @Test
    @Order(1)
    fun `Lagrer FriskTilArbeid vedtaksperiode for to brukere`() {
        arbeidssokerperiodeRepository.save(
            Arbeidssokerperiode(
                fnr = "11111111111",
                vedtaksperiodeId = UUID.randomUUID().toString(),
                opprettet = Instant.now(),
            ),
        )

        arbeidssokerperiodeRepository.save(
            Arbeidssokerperiode(
                fnr = "22222222222",
                vedtaksperiodeId = UUID.randomUUID().toString(),
                opprettet = Instant.now(),
            ),
        )

        arbeidssokerperiodeRepository.findAll().toList().also {
            it.size `should be equal to` 2
        }
    }

    @Test
    @Order(2)
    fun `Sletter data for bruker ved mottatt melding om testdata reset`() {
        val key = UUID.randomUUID().toString()

        kafkaProducer.send(ProducerRecord(TESTDATA_RESET_TOPIC, key, FNR)).get()

        testdataResetConsumer.waitForRecords(1).also {
            it.first().key() `should be equal to` key
            it.first().value() `should be equal to` FNR
        }

        arbeidssokerperiodeRepository.findAll().toList().also {
            it.size `should be equal to` 1
            it.first().fnr `should be equal to` "22222222222"
        }
    }
}
