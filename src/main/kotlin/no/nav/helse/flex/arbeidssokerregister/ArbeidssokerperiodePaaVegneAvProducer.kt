package no.nav.helse.flex.arbeidssokerregister

import no.nav.helse.flex.logger
import no.nav.paw.bekreftelse.paavegneav.v1.PaaVegneAv
import no.nav.paw.bekreftelse.paavegneav.v1.vo.Bekreftelsesloesning
import no.nav.paw.bekreftelse.paavegneav.v1.vo.Start
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerRecord
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import java.util.*

private const val EN_DAG_I_MS = 86_400_000L
const val FJORDEN_DAGER = EN_DAG_I_MS * 14
const val FIRE_MAANEDER = EN_DAG_I_MS * 123

@Component
class ArbeidssokerperiodePaaVegneAvProducer(
    @Qualifier("avroKafkaProducer")
    val kafkaProducer: Producer<Long, PaaVegneAv>,
) {
    private val log = logger()

    fun send(paaVegneAvMelding: PaaVegneAvMelding) {
        val kafkaKey = paaVegneAvMelding.kafkaKey
        val periodeId = paaVegneAvMelding.periodeId

        val paaVegneAv =
            PaaVegneAv(
                periodeId,
                Bekreftelsesloesning.FRISKMELDT_TIL_ARBEIDSFORMIDLING,
                Start(FJORDEN_DAGER, FIRE_MAANEDER),
            )
        kafkaProducer
            .send(
                ProducerRecord(
                    ARBEIDSSOKERPERIODE_PAA_VEGNE_AV_TOPIC,
                    kafkaKey,
                    paaVegneAv,
                ),
            ).get()

        log.info("Sendt PaaVegneAv-melding med kafkaKey: $kafkaKey og periodeId: $periodeId")
    }
}

data class PaaVegneAvMelding(
    val kafkaKey: Long,
    val periodeId: UUID,
)

const val ARBEIDSSOKERPERIODE_PAA_VEGNE_AV_TOPIC =
    "paw.arbeidssoker-bekreftelse-paavegneav-friskmeldt-til-arbeidsformidling-beta-v1"
