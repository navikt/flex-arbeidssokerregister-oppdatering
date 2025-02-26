package no.nav.helse.flex.arbeidssokerregister

import no.nav.helse.flex.arbeidssokerperiode.ArbeidssokerperiodeService
import no.nav.paw.arbeidssokerregisteret.api.v1.Periode
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component

@Component
class ArbeidssokerperiodeListener(
    private val arbeidssokerperiodeService: ArbeidssokerperiodeService,
) {
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
            arbeidssokerperiodeService.behandlePeriode(it)
        }
        acknowledgment.acknowledge()
    }
}

const val ARBEIDSSOKERPERIODE_TOPIC = "paw.arbeidssokerperioder-v1"
