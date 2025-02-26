package no.nav.helse.flex.arbeidssokerregister

import no.nav.paw.bekreftelse.melding.v1.Bekreftelse
import no.nav.paw.bekreftelse.melding.v1.vo.Bekreftelsesloesning
import no.nav.paw.bekreftelse.melding.v1.vo.Bruker
import no.nav.paw.bekreftelse.melding.v1.vo.BrukerType
import no.nav.paw.bekreftelse.melding.v1.vo.Metadata
import no.nav.paw.bekreftelse.melding.v1.vo.Svar
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerRecord
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.*

private const val AARSAK = "Innsending av sykepengesoknad"

private const val UTFOERT_AV = "flex-arbeidssokerregister-oppdatering"

@Component
class ArbeidssokerperiodeBekreftelseProducer(
    @Qualifier("avroKafkaProducer")
    val kafkaProducer: Producer<Long, Bekreftelse>,
) {
    fun send(arbeidssokerperiodeBekreftelse: ArbeidssokerperiodeBekreftelse) {
        kafkaProducer
            .send(
                ProducerRecord(
                    ARBEIDSSOKERPERIODE_BEKREFTELSE_TOPIC,
                    arbeidssokerperiodeBekreftelse.kafkaKey,
                    lagBekreftelse(arbeidssokerperiodeBekreftelse),
                ),
            ).get()
    }

    private fun lagBekreftelse(periodeBekrefelse: ArbeidssokerperiodeBekreftelse): Bekreftelse {
        val metadata =
            Metadata(Instant.now(), Bruker(BrukerType.SLUTTBRUKER, periodeBekrefelse.fnr), UTFOERT_AV, AARSAK)

        val bekreftelse =
            Bekreftelse(
                periodeBekrefelse.periodeId,
                Bekreftelsesloesning.FRISKMELDT_TIL_ARBEIDSFORMIDLING,
                UUID.randomUUID(),
                Svar(
                    metadata,
                    periodeBekrefelse.periodeStart,
                    periodeBekrefelse.periodeSlutt,
                    periodeBekrefelse.inntektUnderveis,
                    periodeBekrefelse.fortsattArbeidssoker,
                ),
            )
        return bekreftelse
    }
}

data class ArbeidssokerperiodeBekreftelse(
    val kafkaKey: Long,
    val periodeId: UUID,
    val fnr: String,
    val periodeStart: Instant,
    val periodeSlutt: Instant,
    val inntektUnderveis: Boolean,
    val fortsattArbeidssoker: Boolean,
)

const val ARBEIDSSOKERPERIODE_BEKREFTELSE_TOPIC =
    "paw.arbeidssoker-bekreftelse-friskmeldt-til-arbeidsformidling-beta-v1"
