package no.nav.helse.flex.arbeidssokerregister

import no.nav.helse.flex.logger
import no.nav.paw.arbeidssokerregisteret.api.v1.Periode
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component

@Component
class ArbeidssokerregisterPeriodeConsumer {
    private val log = logger()

    @KafkaListener(
        topics = [ARBEIDSSOKERREGISTER_PERIODE_TOPIC],
        id = "flex-arbeidssokerregister-periode-v1",
        containerFactory = "avroKafkaListenerContainerFactory",
        properties = ["auto.offset.reset = earliest"],
    )
    fun listen(
        cr: ConsumerRecord<Long, Periode>,
        acknowledgment: Acknowledgment,
    ) {
        cr.value().also {
            log.info("Mottok periode med id: ${it.id}.")
        }
        acknowledgment.acknowledge()
    }
}

const val ARBEIDSSOKERREGISTER_PERIODE_TOPIC = "paw.arbeidssokerperioder-v1"
