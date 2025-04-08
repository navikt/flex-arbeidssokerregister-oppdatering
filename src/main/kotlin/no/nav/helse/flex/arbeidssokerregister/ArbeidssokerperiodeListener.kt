package no.nav.helse.flex.arbeidssokerregister

import io.opentelemetry.instrumentation.annotations.WithSpan
import no.nav.helse.flex.arbeidssokerperiode.ArbeidssokerperiodeService
import no.nav.helse.flex.logger
import no.nav.paw.arbeidssokerregisteret.api.v1.Periode
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component

@Component
class ArbeidssokerperiodeListener(
    private val arbeidssokerperiodeService: ArbeidssokerperiodeService,
) {
    private val log = logger()

    @WithSpan
    @KafkaListener(
        topics = [ARBEIDSSOKERPERIODE_TOPIC],
        id = "flex-arbeidssokerregister-oppdatering-periode-v3",
        containerFactory = "avroKafkaListenerContainerFactory",
        concurrency = "6",
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
