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
        id = "flex-arbeidssokerregister-oppdatering-sykepengesoknad-v1",
        containerFactory = "kafkaListenerContainerFactory",
        properties = ["auto.offset.reset = latest"],
    )
    fun listen(
        cr: ConsumerRecord<String, String>,
        acknowledgment: Acknowledgment,
    ) {
        cr.value().tilSykepengesoknadDTO().also {
            try {
                sykepengesoknadService.behandleSoknad(it)
            } catch (e: Exception) {
                if (!environmentToggles.erProduksjon()) {
                    val clusterName = environmentToggles.naisClusterName()
                    log.warn("Feil ved behandling av søknad: ${it.id} i $clusterName: ${e.message}", e)
                } else {
                    throw e
                }
            }
        }
        acknowledgment.acknowledge()
    }
}

const val SYKEPENGESOKNAD_TOPIC = "flex.sykepengesoknad"

fun String.tilSykepengesoknadDTO(): SykepengesoknadDTO = objectMapper.readValue(this)
