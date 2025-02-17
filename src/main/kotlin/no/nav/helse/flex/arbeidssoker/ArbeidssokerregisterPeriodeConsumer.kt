package no.nav.helse.flex.arbeidssoker

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
        topics = [PAW_ARBEIDSOKERPERIODE_TOPIC],
        id = "flex-arbeidssokerregister-oppdatering-periode-v2",
        containerFactory = "arbeidssokerregisterPeriodeListenerContainerFactory",
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

const val PAW_ARBEIDSOKERPERIODE_TOPIC = "paw.arbeidssokerperioder-v1"
