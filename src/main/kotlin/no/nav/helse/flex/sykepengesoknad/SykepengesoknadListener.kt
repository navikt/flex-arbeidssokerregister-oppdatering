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
    )
    fun listen(
        cr: ConsumerRecord<String, String>,
        acknowledgment: Acknowledgment,
    ) {
        cr.value().tilSykepengesoknadDTO().also {
            if (
                it.friskTilArbeidVedtakId in
                listOf(
                    "fe691fa4-245f-4bd9-abfd-1222b9353627",
                )
            ) {
                log.info(
                    "Ignorerer søknad: ${it.id} og vedtaksperiodeId: ${it.friskTilArbeidVedtakId} siden bruker " +
                        "ikke skal være regsitert som arbeidssøker.",
                )
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
