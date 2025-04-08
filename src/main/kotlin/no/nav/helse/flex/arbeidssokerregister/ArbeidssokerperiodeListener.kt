package no.nav.helse.flex.arbeidssokerregister

import io.opentelemetry.instrumentation.annotations.WithSpan
import no.nav.helse.flex.arbeidssokerperiode.ArbeidssokerperiodeService
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
    @WithSpan
    @KafkaListener(
        topics = [ARBEIDSSOKERPERIODE_TOPIC],
        id = "flex-arbeidssokerregister-oppdatering-periode-v2",
        containerFactory = "avroKafkaListenerContainerFactory",
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

    override fun onPartitionsAssigned(
        assignments: Map<org.apache.kafka.common.TopicPartition?, Long?>,
        callback: ConsumerSeekCallback,
    ) {
        val startTimestamp = LocalDate.of(2025, 3, 20).toInstantAtStartOfDay().toEpochMilli()

        assignments.keys.filterNotNull().forEach { topicPartition ->
            callback.seekToTimestamp(topicPartition.topic(), topicPartition.partition(), startTimestamp)
        }
    }
}

const val ARBEIDSSOKERPERIODE_TOPIC = "paw.arbeidssokerperioder-v1"
