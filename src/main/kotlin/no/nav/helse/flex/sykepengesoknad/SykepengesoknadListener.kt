package no.nav.helse.flex.sykepengesoknad

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.helse.flex.ArbeidssokerregisterService
import no.nav.helse.flex.logger
import no.nav.helse.flex.objectMapper
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadsstatusDTO
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadstypeDTO
import no.nav.helse.flex.sykepengesoknad.kafka.SykepengesoknadDTO
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component

@Component
class SykepengesoknadListener(
    private val arbeidssokerregisterService: ArbeidssokerregisterService,
) {
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
            if (it.skalProsesseres()) {
                log.info("Behandler søknad av type ${it.type} med id: ${it.id} og status: ${it.status}.")
                log.info(
                    "friskTilArbeidVedtakId: ${it.friskTilArbeidVedtakId}, friskTilArbeidVedtakPeriode: ${it.friskTilArbeidVedtakPeriode}, fortsattArbeidssoker: ${it.fortsattArbeidssoker}",
                )
                // TODO: Hent data fra DTO.
                // TODO: Opprett Domene-objekt for ArbeidssokrregisterService.prosesserSoknad.
                arbeidssokerregisterService.prosesserSoknad(it)
            }
        }
        acknowledgment.acknowledge()
    }

    private fun SykepengesoknadDTO.skalProsesseres() =
        type == SoknadstypeDTO.FRISKMELDT_TIL_ARBEIDSFORMIDLING && status == SoknadsstatusDTO.FREMTIDIG
}

const val SYKEPENGESOKNAD_TOPIC = "flex.sykepengesoknad"

fun String.tilSykepengesoknadDTO(): SykepengesoknadDTO = objectMapper.readValue(this)
