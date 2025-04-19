package no.nav.helse.flex.sykepengesoknad

import io.opentelemetry.instrumentation.annotations.WithSpan
import no.nav.helse.flex.EnvironmentToggles
import no.nav.helse.flex.logger
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadstypeDTO
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.listener.ConsumerSeekAware
import org.springframework.kafka.listener.ConsumerSeekAware.ConsumerSeekCallback
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component
import java.time.LocalDate

@Component
class SykepengesoknadExceptionListener(
    private val sykepengesoknadService: SykepengesoknadService,
    private val environmentToggles: EnvironmentToggles,
    private val vedtaksperiodeExceptionRepository: VedtaksperiodeExceptionRepository,
) : ConsumerSeekAware {
    private val log = logger()

    @WithSpan
    @KafkaListener(
        topics = [SYKEPENGESOKNAD_TOPIC],
        id = "flex-arbeidssokerregister-oppdatering-uregistert-v1",
        containerFactory = "kafkaListenerContainerFactory",
        properties = ["auto.offset.reset = earliest"],
        concurrency = "3",
    )
    fun listen(
        cr: ConsumerRecord<String, String>,
        acknowledgment: Acknowledgment,
    ) {
        val sykepengesoknad = cr.value().tilSykepengesoknadDTO()

        if (!environmentToggles.erProduksjon()) {
            acknowledgment.acknowledge()
            return
        }

        if (sykepengesoknad.type != SoknadstypeDTO.FRISKMELDT_TIL_ARBEIDSFORMIDLING) {
            acknowledgment.acknowledge()
            return
        }

        try {
            if (sykepengesoknad.friskTilArbeidVedtakId == "1d2cc925-d24c-4e58-b2cf-7b9f32421ddd") {
                sykepengesoknadService.behandleSoknad(sykepengesoknad)
            }
        } catch (e: Exception) {
            log.warn(
                "Feil ved reprosessering av søknad i vedtaksperiode_exception: ${sykepengesoknad.id} med " +
                    "vedtaksperiodeId: ${sykepengesoknad.friskTilArbeidVedtakId}.",
                e,
            )
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
