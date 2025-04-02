package no.nav.helse.flex.sykepengesoknad

import com.fasterxml.jackson.module.kotlin.readValue
import io.opentelemetry.instrumentation.annotations.WithSpan
import no.nav.helse.flex.EnvironmentToggles
import no.nav.helse.flex.logger
import no.nav.helse.flex.objectMapper
import no.nav.helse.flex.sykepengesoknad.kafka.SykepengesoknadDTO
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component

@Component
class SykepengesoknadListener(
    private val sykepengesoknadService: SykepengesoknadService,
    private val environmentToggles: EnvironmentToggles,
) {
    private val log = logger()

    @WithSpan
    @KafkaListener(
        topics = [SYKEPENGESOKNAD_TOPIC],
        id = "flex-arbeidssokerregister-oppdatering-sykepengesoknad-v5",
        containerFactory = "kafkaListenerContainerFactory",
        properties = ["auto.offset.reset = earliest"],
    )
    fun listen(
        cr: ConsumerRecord<String, String>,
        acknowledgment: Acknowledgment,
    ) {
        cr.value().tilSykepengesoknadDTO().also {
            // TODO: Slett når SYKEPENGESOKNAD_TOPIC er prosessert på nytt.
            if (
                it.friskTilArbeidVedtakId in
                listOf(
                    "4fe42342-7102-44d8-acd9-dc6a4f226f5a",
                    "e4504199-f052-469a-9e0d-bffd1bad6bef",
                    "688142df-92d9-4f44-b176-fd74d0c5da1d",
                    "bedb05d3-a2ff-4ee3-8525-44965b21442c",
                    "5abde058-fc10-470f-a336-0daccd7ed733",
                    "fe691fa4-245f-4bd9-abfd-1222b9353627",
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
}

const val SYKEPENGESOKNAD_TOPIC = "flex.sykepengesoknad"

fun String.tilSykepengesoknadDTO(): SykepengesoknadDTO = objectMapper.readValue(this)
