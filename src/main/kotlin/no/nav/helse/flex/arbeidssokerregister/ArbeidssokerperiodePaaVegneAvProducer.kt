package no.nav.helse.flex.arbeidssokerregister

import no.nav.helse.flex.logger
import no.nav.paw.bekreftelse.paavegneav.v1.PaaVegneAv
import no.nav.paw.bekreftelse.paavegneav.v1.vo.Bekreftelsesloesning
import no.nav.paw.bekreftelse.paavegneav.v1.vo.Start
import no.nav.paw.bekreftelse.paavegneav.v1.vo.Stopp
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerRecord
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import java.util.*

private const val EN_DAG_I_MS = 86_400_000L
const val FJORDEN_DAGER = EN_DAG_I_MS * 14

@Component
class ArbeidssokerperiodePaaVegneAvProducer(
    @Qualifier("avroKafkaProducer")
    val kafkaProducer: Producer<Long, PaaVegneAv>,
) {
    private val log = logger()

    fun send(paaVegneAvMelding: PaaVegneAvStartMelding) {
        val kafkaKey = paaVegneAvMelding.kafkaKey
        val periodeId = paaVegneAvMelding.periodeId

        val paaVegneAv =
            PaaVegneAv(
                periodeId,
                Bekreftelsesloesning.FRISKMELDT_TIL_ARBEIDSFORMIDLING,
                Start(FJORDEN_DAGER, paaVegneAvMelding.graceMS),
            )

        sendKafkaMelding(kafkaKey, paaVegneAv)

        log.info("Publisert PaaVegneAvStartMelding for periode i arbeidssøkerregisteret: $periodeId.")
    }

    fun send(paaVegneAvMelding: PaaVegneAvStoppMelding) {
        val kafkaKey = paaVegneAvMelding.kafkaKey
        val periodeId = paaVegneAvMelding.periodeId

        val paaVegneAv =
            PaaVegneAv(
                periodeId,
                Bekreftelsesloesning.FRISKMELDT_TIL_ARBEIDSFORMIDLING,
                Stopp(),
            )

        sendKafkaMelding(kafkaKey, paaVegneAv)

        log.info("Publisert PaaVegneAvStoppMelding for periode i arbeidssøkerregisteret: $periodeId.")
    }

    private fun sendKafkaMelding(
        kafkaKey: Long,
        paaVegneAv: PaaVegneAv,
    ) {
        kafkaProducer
            .send(
                ProducerRecord(
                    ARBEIDSSOKERPERIODE_PAA_VEGNE_AV_TOPIC,
                    kafkaKey,
                    paaVegneAv,
                ),
            ).get()
    }
}

data class PaaVegneAvStartMelding(
    val kafkaKey: Long,
    val periodeId: UUID,
    val graceMS: Long,
)

data class PaaVegneAvStoppMelding(
    val kafkaKey: Long,
    val periodeId: UUID,
)

const val ARBEIDSSOKERPERIODE_PAA_VEGNE_AV_TOPIC =
    "paw.arbeidssoker-bekreftelse-paavegneav-friskmeldt-til-arbeidsformidling-v1"
