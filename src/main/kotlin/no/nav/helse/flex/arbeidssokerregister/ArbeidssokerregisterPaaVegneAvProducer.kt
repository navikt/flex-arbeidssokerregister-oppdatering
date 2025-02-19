package no.nav.helse.flex.arbeidssokerregister

import no.nav.paw.bekreftelse.paavegneav.v1.PaaVegneAv
import no.nav.paw.bekreftelse.paavegneav.v1.vo.Bekreftelsesloesning
import no.nav.paw.bekreftelse.paavegneav.v1.vo.Start
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerRecord
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import java.util.*

private const val EN_UKE = 3600 * 1000 * 24 * 7
private const val TO_UKER = EN_UKE * 2L
private const val FIRE_UKER = EN_UKE * 4L

@Component
class ArbeidssokerregisterPaaVegneAvProducer(
    @Qualifier("avroKafkaProducer")
    val kafkaProducer: Producer<Long, PaaVegneAv>,
) {
    fun send(paaVegneAvMelding: PaaVegneAvMelding) {
        val paaVegneAv =
            PaaVegneAv(
                paaVegneAvMelding.periodeId,
                Bekreftelsesloesning.FRISKMELDT_TIL_ARBEIDSFORMIDLING,
                Start(TO_UKER, FIRE_UKER),
            )
        kafkaProducer
            .send(
                ProducerRecord(
                    ARBEIDSSOKERREGISTER_PAA_VEGNE_AV_TOPIC,
                    paaVegneAvMelding.kafkaKey,
                    paaVegneAv,
                ),
            ).get()
    }
}

data class PaaVegneAvMelding(
    val kafkaKey: Long,
    val periodeId: UUID,
)

const val ARBEIDSSOKERREGISTER_PAA_VEGNE_AV_TOPIC =
    "paw.arbeidssoker-bekreftelse-paavegneav-friskmeldt-til-arbeidsformidling-beta-v1"
