package no.nav.helse.flex.arbeidssokerregister

import no.nav.helse.flex.FellesTestOppsett
import no.nav.paw.arbeidssokerregisteret.api.v1.AvviksType
import no.nav.paw.arbeidssokerregisteret.api.v1.Periode
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

class ArbeidssokerperiodeConsumerTest : FellesTestOppsett() {
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
        val bruker =
            no.nav.paw.arbeidssokerregisteret.api.v1
                .Bruker(no.nav.paw.arbeidssokerregisteret.api.v1.BrukerType.UDEFINERT, "123")
        val tidspunktFraKilde =
            no.nav.paw.arbeidssokerregisteret.api.v1
                .TidspunktFraKilde(Instant.now(), AvviksType.FORSINKELSE)
        val startet =
            no.nav.paw.arbeidssokerregisteret.api.v1
                .Metadata(Instant.now(), bruker, "", "", tidspunktFraKilde)
        val periode = Periode(UUID.randomUUID(), "123", startet, null)

        kafkaProducer.send(ProducerRecord(ARBEIDSSOKERPERIODE_TOPIC, 10L, periode)).get()

        await().atMost(1, TimeUnit.SECONDS).untilAsserted {
            arbeidssokerperiodeListener.hentPeriode(periode.id.toString()) `should not be` null
        }

        arbeidssokerperiodeConsumer.waitForRecords(1).also {
            it.first().key() `should not be` null
            it.first().value() `should not be` null
        }
    }
}
