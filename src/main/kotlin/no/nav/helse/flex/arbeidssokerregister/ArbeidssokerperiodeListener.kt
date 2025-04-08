package no.nav.helse.flex.arbeidssokerregister

import io.opentelemetry.instrumentation.annotations.WithSpan
import no.nav.helse.flex.arbeidssokerperiode.ArbeidssokerperiodeService
import no.nav.helse.flex.logger
import no.nav.helse.flex.sykepengesoknad.toInstantAtStartOfDay
import no.nav.paw.arbeidssokerregisteret.api.v1.Periode
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.listener.ConsumerSeekAware
import org.springframework.kafka.listener.ConsumerSeekAware.ConsumerSeekCallback
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component
import java.time.LocalDate

@Component
class ArbeidssokerperiodeListener(
    private val arbeidssokerperiodeService: ArbeidssokerperiodeService,
) : ConsumerSeekAware {
    private val log = logger()

    @WithSpan
    @KafkaListener(
        topics = [ARBEIDSSOKERPERIODE_TOPIC],
        id = "flex-arbeidssokerregister-oppdatering-periode-v3",
        containerFactory = "avroKafkaListenerContainerFactory",
        properties = ["auto.offset.reset=earliest"],
        concurrency = "6",
    )
    fun listen(
        cr: ConsumerRecord<Long, Periode>,
        acknowledgment: Acknowledgment,
    ) {
        cr.value().also {
            val leterEtter =
                listOf(
                    "2540f522-e7f9-4dfb-9e4d-2134c9f57df1",
                    "14831388-d299-4291-9ae1-60a0bb5881a8",
                    "7249ce01-ad44-4ddb-9285-dde2b74bec94",
                )
            if (leterEtter.contains(it.id.toString())) {
                log.info(
                    "Fant arbeidssokerperiode med id: ${it.id} på partition: ${cr.partition()} " +
                        "og offset: ${cr.offset()} som er avsluttet: ${it.avsluttet != null}.",
                )
            }

            arbeidssokerperiodeService.behandlePeriode(it)
        }
        acknowledgment.acknowledge()
    }

    override fun onPartitionsAssigned(
        assignments: Map<org.apache.kafka.common.TopicPartition?, Long?>,
        callback: ConsumerSeekCallback,
    ) {
        val startTimestamp = LocalDate.of(2025, 3, 1).toInstantAtStartOfDay().toEpochMilli()

        assignments.keys.filterNotNull().forEach { topicPartition ->
            callback.seekToTimestamp(topicPartition.topic(), topicPartition.partition(), startTimestamp)
        }
    }
}

const val ARBEIDSSOKERPERIODE_TOPIC = "paw.arbeidssokerperioder-v1"
