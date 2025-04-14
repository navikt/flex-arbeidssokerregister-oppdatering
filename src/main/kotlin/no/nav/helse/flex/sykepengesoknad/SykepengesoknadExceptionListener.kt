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
        id = "flex-arbeidssokerregister-oppdatering-exception-v3",
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

        if (sykepengesoknad.friskTilArbeidVedtakId in
            listOf(
                "4fe42342-7102-44d8-acd9-dc6a4f226f5a",
                "e4504199-f052-469a-9e0d-bffd1bad6bef",
                "688142df-92d9-4f44-b176-fd74d0c5da1d",
                "bedb05d3-a2ff-4ee3-8525-44965b21442c",
                "5abde058-fc10-470f-a336-0daccd7ed733",
                "fe691fa4-245f-4bd9-abfd-1222b9353627",
            )
        ) {
            acknowledgment.acknowledge()
            return
        }

        try {
            if (vedtaksperiodeExceptionRepository.findBySykepengesoknadId(sykepengesoknad.id).isNotEmpty()) {
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
