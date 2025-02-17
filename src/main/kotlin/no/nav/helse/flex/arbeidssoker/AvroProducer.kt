package no.nav.helse.flex.arbeidssoker

import no.nav.helse.flex.logger
import no.nav.paw.bekreftelse.paavegneav.v1.PaaVegneAv
import no.nav.paw.bekreftelse.paavegneav.v1.vo.Bekreftelsesloesning
import no.nav.paw.bekreftelse.paavegneav.v1.vo.Start
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerRecord
import org.springframework.stereotype.Component
import java.util.UUID

private const val EN_UKE = 3600 * 1000 * 24 * 7
private const val TO_UKER = EN_UKE * 2L
private const val FIRE_UKER = EN_UKE * 4L

@Component
class AvroProducer(
    val arbeidssokerregisterPaaVegneAvProducer: Producer<Long, PaaVegneAv>,
) {
    private val log = logger()

    private val kafkaKey: Long = -3934
    private val periodeId: String = "bf26fb09-9efa-43cc-8f8e-4afdfcc4596b"

    fun send(): Pair<Long, String> {
        val paaVegneAv =
            PaaVegneAv(
                UUID.fromString(periodeId),
                Bekreftelsesloesning.FRISKMELDT_TIL_ARBEIDSFORMIDLING,
                Start(TO_UKER, FIRE_UKER),
            )
        arbeidssokerregisterPaaVegneAvProducer.send(
            ProducerRecord(
                ARBEIDSSOKERREGISTER_PAA_VEGNE_AV_TOPIC,
                kafkaKey,
                paaVegneAv,
            ),
        )
        log.info("Sendt PaaVegneAv-melding med key $kafkaKey og periodeId $periodeId.")
        return kafkaKey to periodeId
    }
}

const val ARBEIDSSOKERREGISTER_PAA_VEGNE_AV_TOPIC = "paw.arbeidssoker-bekreftelse-paavegneav-beta-v1"
