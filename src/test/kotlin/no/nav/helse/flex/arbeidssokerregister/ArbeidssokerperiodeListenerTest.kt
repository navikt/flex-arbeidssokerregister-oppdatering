package no.nav.helse.flex.arbeidssokerregister

import no.nav.helse.flex.FellesTestOppsett
import no.nav.paw.arbeidssokerregisteret.api.v1.Bruker
import no.nav.paw.arbeidssokerregisteret.api.v1.BrukerType
import no.nav.paw.arbeidssokerregisteret.api.v1.Metadata
import no.nav.paw.arbeidssokerregisteret.api.v1.Periode
import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.`should not be`
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerRecord
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import java.time.Instant
import java.util.*
import java.util.concurrent.TimeUnit

class ArbeidssokerperiodeListenerTest : FellesTestOppsett() {
    @Autowired
    private val arbeidssokerperiodeListener = ArbeidssokerperiodeListener()

    @Autowired
    @Qualifier("avroKafkaProducer")
    private lateinit var kafkaProducer: Producer<Long, Periode>

    @Autowired
    private lateinit var arbeidssokerperiodeConsumer: Consumer<Long, Periode>

    @BeforeAll
    fun subscribeToTopics() {
        arbeidssokerperiodeConsumer.subscribeToTopics(ARBEIDSSOKERPERIODE_TOPIC)
    }

    @Test
    fun `Kan motta og deserialisere Periode`() {
        val startet = Metadata(Instant.now(), Bruker(BrukerType.SYSTEM, "TEST"), "Test", "Test", null)
        val periode = Periode(UUID.randomUUID(), "11111111111", startet, null)

        kafkaProducer.send(ProducerRecord(ARBEIDSSOKERPERIODE_TOPIC, 10L, periode)).get()

        await().atMost(1, TimeUnit.SECONDS).untilAsserted {
            arbeidssokerperiodeListener.hentPeriode(periode.id.toString()) `should not be` null
        }

        arbeidssokerperiodeConsumer.waitForRecords(1).first().also {
            it.key() `should be equal to` 10L
            it.value().id `should be equal to` periode.id
        }
    }
}
