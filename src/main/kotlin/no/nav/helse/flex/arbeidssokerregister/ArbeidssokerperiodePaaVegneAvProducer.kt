package no.nav.helse.flex.arbeidssokerregister

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import io.opentelemetry.instrumentation.annotations.WithSpan
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
    @param:Qualifier("avroKafkaProducer")
    val kafkaProducer: Producer<Long, PaaVegneAv>,
) {
    private val log = logger()

    @WithSpan
    fun send(paaVegneAvMelding: PaaVegneAvStartMelding) {
        val kafkaKey = paaVegneAvMelding.kafkaKey

        val paaVegneAv =
            PaaVegneAv(
                UUID.fromString(paaVegneAvMelding.arbeidssokerregisterPeriodeId),
                Bekreftelsesloesning.FRISKMELDT_TIL_ARBEIDSFORMIDLING,
                Start(FJORDEN_DAGER, paaVegneAvMelding.graceMS),
            )

        Span.current().addEvent(
            "PaaVegneAvStartMelding",
            Attributes.of(
                AttributeKey.stringKey("periodeId"),
                paaVegneAvMelding.arbeidssokerregisterPeriodeId,
                AttributeKey.stringKey("graceMs"),
                paaVegneAvMelding.graceMS.toString(),
            ),
        )
        sendKafkaMelding(kafkaKey, paaVegneAv)

        log.info(
            "Publisert PaaVegneAvStartMelding for arbeidsøkerperiode: ${paaVegneAvMelding.arbeidssokerperiodeId} " +
                "og periode i arbeidssøkerregisteret: ${paaVegneAvMelding.arbeidssokerregisterPeriodeId}.",
        )
    }

    @WithSpan
    fun send(paaVegneAvMelding: PaaVegneAvStoppMelding) {
        val kafkaKey = paaVegneAvMelding.kafkaKey

        val stopp =
            if (paaVegneAvMelding.arbeidssokerperiodeId == "7ff8d622-ab77-40d4-a6ca-a98e32f376ba" &&
                paaVegneAvMelding.arbeidssokerregisterPeriodeId == "6ba36b7e-0d3d-4304-820d-dd63888330c0"
            ) {
                Stopp(true)
                log.info("Sendt Stopp med fristBrutt = true for arbeidssokerperiode: ${paaVegneAvMelding.arbeidssokerperiodeId}")
            } else {
                Stopp()
            }

        val paaVegneAv =
            PaaVegneAv(
                UUID.fromString(paaVegneAvMelding.arbeidssokerregisterPeriodeId),
                Bekreftelsesloesning.FRISKMELDT_TIL_ARBEIDSFORMIDLING,
                stopp,
            )

        Span.current().addEvent(
            "PaaVegneAvStoppMelding",
            Attributes.of(
                AttributeKey.stringKey("periodeId"),
                paaVegneAvMelding.arbeidssokerregisterPeriodeId,
            ),
        )
        sendKafkaMelding(kafkaKey, paaVegneAv)

        log.info(
            "Publisert PaaVegneAvStoppMelding for arbeidsøkerperiode: ${paaVegneAvMelding.arbeidssokerperiodeId} " +
                "og periode i arbeidssøkerregisteret: ${paaVegneAvMelding.arbeidssokerregisterPeriodeId}.",
        )
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
    val arbeidssokerperiodeId: String,
    val arbeidssokerregisterPeriodeId: String,
    val graceMS: Long,
)

data class PaaVegneAvStoppMelding(
    val kafkaKey: Long,
    val arbeidssokerperiodeId: String,
    val arbeidssokerregisterPeriodeId: String,
)

const val ARBEIDSSOKERPERIODE_PAA_VEGNE_AV_TOPIC =
    "paw.arbeidssoker-bekreftelse-paavegneav-friskmeldt-til-arbeidsformidling-v1"
