package no.nav.helse.flex.arbeidssokerregister

import no.nav.helse.flex.logger
import no.nav.paw.arbeidssokerregisteret.api.v1.Periode
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component

@Component
class ArbeidssokerperiodeListener {
    private val log = logger()

    @KafkaListener(
        topics = [ARBEIDSSOKERPERIODE_TOPIC],
        id = "flex-arbeidssokerregister-periode-v1",
        containerFactory = "avroKafkaListenerContainerFactory",
        properties = ["auto.offset.reset = earliest"],
    )
    fun listen(
        cr: ConsumerRecord<Long, Periode>,
        acknowledgment: Acknowledgment,
    ) {
        cr.value().also {
            perioder[it.id.toString()] = it
            log.info("Mottok periode med id: ${it.id}.")
        }
        acknowledgment.acknowledge()
    }

    // TODO: Erstatt med @Repository og TestContainers.
    private val perioder = mutableMapOf<String, Periode>()

    fun hentPeriode(id: String): Periode? = perioder[id]
}

const val ARBEIDSSOKERPERIODE_TOPIC = "paw.arbeidssokerperioder-v1"
