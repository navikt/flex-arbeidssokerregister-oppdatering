package no.nav.helse.flex.arbeidssokerregister

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
        val periodeBekreftelse =
            PeriodeBekreftelse(1, UUID.randomUUID(), "11111111111", Instant.now(), Instant.now(), true, true)

        bekreftelseProducer.send(periodeBekreftelse)

        bekreftelseConsumer.waitForRecords(1).first().also {
            it.key() `should be equal to` periodeBekreftelse.kafkaKey
            it.value().periodeId `should be equal to` periodeBekreftelse.periodeId
        }
    }
}
