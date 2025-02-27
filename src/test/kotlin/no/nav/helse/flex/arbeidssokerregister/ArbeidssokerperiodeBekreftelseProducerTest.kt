package no.nav.helse.flex.arbeidssokerregister

import no.nav.helse.flex.FNR
import no.nav.helse.flex.FellesTestOppsett
import no.nav.paw.bekreftelse.melding.v1.Bekreftelse
import org.amshove.kluent.`should be equal to`
import org.apache.kafka.clients.consumer.Consumer
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.Instant
import java.util.*

class ArbeidssokerperiodeBekreftelseProducerTest : FellesTestOppsett() {
    @Autowired
    private lateinit var bekreftelseProducer: ArbeidssokerperiodeBekreftelseProducer

    @Autowired
    private lateinit var bekreftelseConsumer: Consumer<Long, Bekreftelse>

    @BeforeAll
    fun subscribeToTopics() {
        bekreftelseConsumer.subscribeToTopics(ARBEIDSSOKERPERIODE_BEKREFTELSE_TOPIC)
    }

    @Test
    fun `Kan serialisere og sende Bekreftelse`() {
        val arbeidssokerperiodeBekreftelse =
            ArbeidssokerperiodeBekreftelse(1, UUID.randomUUID(), FNR, Instant.now(), Instant.now(), true, true)

        bekreftelseProducer.send(arbeidssokerperiodeBekreftelse)

        bekreftelseConsumer.waitForRecords(1).single().also {
            it.key() `should be equal to` arbeidssokerperiodeBekreftelse.kafkaKey
            it.value().periodeId `should be equal to` arbeidssokerperiodeBekreftelse.periodeId
        }
    }
}
