package no.nav.helse.flex.sykepengesoknad

import com.fasterxml.jackson.module.kotlin.readValue
import io.opentelemetry.instrumentation.annotations.WithSpan
import no.nav.helse.flex.EnvironmentToggles
import no.nav.helse.flex.logger
import no.nav.helse.flex.objectMapper
import no.nav.helse.flex.sykepengesoknad.kafka.SykepengesoknadDTO
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.listener.ConsumerSeekAware
import org.springframework.kafka.listener.ConsumerSeekAware.ConsumerSeekCallback
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component
import java.time.LocalDate

@Component
class SykepengesoknadListener(
    private val sykepengesoknadService: SykepengesoknadService,
    private val environmentToggles: EnvironmentToggles,
) : ConsumerSeekAware {
    private val log = logger()

    @WithSpan
    @KafkaListener(
        topics = [SYKEPENGESOKNAD_TOPIC],
        id = "flex-arbeidssokerregister-oppdatering-sykepengesoknad-v4",
        containerFactory = "kafkaListenerContainerFactory",
        properties = ["auto.offset.reset = earliest"],
    )
    fun listen(
        cr: ConsumerRecord<String, String>,
        acknowledgment: Acknowledgment,
    ) {
        cr.value().tilSykepengesoknadDTO().also {
            if (
                // Ikke prosesser søknader som er slettet, men som ligger på Kafka.
                it.friskTilArbeidVedtakId in
                listOf(
                    "4fe42342-7102-44d8-acd9-dc6a4f226f5a",
                    "e4504199-f052-469a-9e0d-bffd1bad6bef",
                    "688142df-92d9-4f44-b176-fd74d0c5da1d",
                )
            ) {
                return
            }

            try {
                sykepengesoknadService.behandleSoknad(it)
            } catch (e: Exception) {
                log.error(
                    "Feil ved behandling av søknad: ${it.id} med vedtaksperiodeId: ${it.friskTilArbeidVedtakId}.",
                    e,
                )
                if (environmentToggles.erProduksjon()) {
                    throw e
                }
            }
        }
        acknowledgment.acknowledge()
    }

    override fun onPartitionsAssigned(
        assignments: Map<org.apache.kafka.common.TopicPartition?, Long?>,
        callback: ConsumerSeekCallback,
    ) {
        // Startet lytting på iSyfo-topic 20. mars 00:00:00.
        val startTimestamp = LocalDate.of(2025, 3, 20).toInstantAtStartOfDay().toEpochMilli()

        assignments.keys.filterNotNull().forEach { topicPartition ->
            callback.seekToTimestamp(topicPartition.topic(), topicPartition.partition(), startTimestamp)
        }
    }
}

const val SYKEPENGESOKNAD_TOPIC = "flex.sykepengesoknad"

fun String.tilSykepengesoknadDTO(): SykepengesoknadDTO = objectMapper.readValue(this)
