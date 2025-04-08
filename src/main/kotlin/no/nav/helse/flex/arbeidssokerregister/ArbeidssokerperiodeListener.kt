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
        id = "flex-arbeidssokerregister-oppdatering-periode-v",
        containerFactory = "avroKafkaListenerContainerFactory",
        properties = ["auto.offset.reset=earliest"],
        concurrency = "3",
    )
    fun listen(
        cr: ConsumerRecord<Long, Periode>,
        acknowledgment: Acknowledgment,
    ) {
        cr.value().also {
            arbeidssokerperiodeService.behandlePeriode(it)
            if (it.id.toString() == "7249ce01-ad44-4ddb-9285-dde2b74bec94") {
                log.info("Fant arbeidssokerperiode med id: ${it.id} på partition: ${cr.partition()} og offset: ${cr.offset()}.")
            }
        }
        acknowledgment.acknowledge()
    }
}

const val ARBEIDSSOKERPERIODE_TOPIC = "paw.arbeidssokerperioder-v1"
