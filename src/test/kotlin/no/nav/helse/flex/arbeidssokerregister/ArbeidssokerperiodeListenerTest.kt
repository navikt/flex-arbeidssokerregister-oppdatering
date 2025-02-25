package no.nav.helse.flex.arbeidssokerregister

import no.nav.helse.flex.FNR
import no.nav.helse.flex.FellesTestOppsett
import no.nav.paw.arbeidssokerregisteret.api.v1.Bruker
import no.nav.paw.arbeidssokerregisteret.api.v1.BrukerType
import no.nav.paw.arbeidssokerregisteret.api.v1.Metadata
import no.nav.paw.arbeidssokerregisteret.api.v1.Periode
import org.amshove.kluent.`should be equal to`
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerRecord
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import java.time.Instant
import java.util.*

class ArbeidssokerperiodeListenerTest : FellesTestOppsett() {
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
        val startet = Metadata(Instant.now(), Bruker(BrukerType.SYSTEM, FNR), "Test", "Test", null)
        val periode = Periode(UUID.randomUUID(), FNR, startet, null)

        kafkaProducer.send(ProducerRecord(ARBEIDSSOKERPERIODE_TOPIC, 10L, periode)).get()

        arbeidssokerperiodeConsumer.waitForRecords(1).first().also {
            it.key() `should be equal to` 10L
            it.value().id `should be equal to` periode.id
        }
    }
}
