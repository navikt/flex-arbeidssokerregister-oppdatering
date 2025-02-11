package no.nav.helse.flex.sykepengesoknad

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.helse.flex.logger
import no.nav.helse.flex.objectMapper
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadstypeDTO
import no.nav.helse.flex.sykepengesoknad.kafka.SykepengesoknadDTO
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component

@Component
class SykepengesoknadConsumer {
    private val log = logger()

    @KafkaListener(
        topics = [SYKEPENGESOKNAD_TOPIC],
        id = "flex-arbeidssokerregister-oppdatering-sykepengesoknad",
        containerFactory = "kafkaListenerContainerFactory",
        properties = ["auto.offset.reset = latest"],
    )
    fun listen(
        cr: ConsumerRecord<String, String>,
        acknowledgment: Acknowledgment,
    ) {
        cr.value().tilSykepengesoknadDTO().also {
            if (it.type == SoknadstypeDTO.FRISKMELDT_TIL_ARBEIDSFORMIDLING) {
                log.info("Mottok ${it.status} søknad av type FRISKMELDT_TIL_ARBEIDSFORMIDLING med id: ${it.id}.")
            }
        }
        acknowledgment.acknowledge()
    }
}

const val SYKEPENGESOKNAD_TOPIC = "flex.sykepengesoknad"

fun String.tilSykepengesoknadDTO(): SykepengesoknadDTO = objectMapper.readValue(this)
