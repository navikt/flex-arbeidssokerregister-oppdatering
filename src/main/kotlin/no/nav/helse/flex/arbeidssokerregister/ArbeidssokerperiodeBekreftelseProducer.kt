package no.nav.helse.flex.arbeidssokerregister

import no.nav.helse.flex.logger
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
    private val log = logger()

    fun send(bekreftelseMelding: BekreftelseMelding) {
        val kafkaKey = bekreftelseMelding.kafkaKey
        val periodeId = bekreftelseMelding.periodeId

        kafkaProducer
            .send(
                ProducerRecord(
                    ARBEIDSSOKERPERIODE_BEKREFTELSE_TOPIC,
                    kafkaKey,
                    lagBekreftelse(bekreftelseMelding),
                ),
            ).get()

        log.info(
            "Publisert Bekreftelse med fortsattArbeidssoker: ${bekreftelseMelding.fortsattArbeidssoker} og " +
                "inntektUnderveis: ${bekreftelseMelding.inntektUnderveis} for periode i arbeidssøkerregisteret: $periodeId.",
        )
    }

    private fun lagBekreftelse(periodeBekreftelse: BekreftelseMelding): Bekreftelse {
        val metadata =
            Metadata(Instant.now(), Bruker(BrukerType.SLUTTBRUKER, periodeBekreftelse.fnr), UTFOERT_AV, AARSAK)

        val bekreftelse =
            Bekreftelse(
                periodeBekreftelse.periodeId,
                Bekreftelsesloesning.FRISKMELDT_TIL_ARBEIDSFORMIDLING,
                UUID.randomUUID(),
                Svar(
                    metadata,
                    periodeBekreftelse.periodeStart,
                    periodeBekreftelse.periodeSlutt,
                    // inntektUnderveis og fortsattArbeidssoker kan være null hvis det er siste søknad i periode og
                    // da skal ikke Periodebekreftelse sendes, så de kan trygt settes til false siden de ikke
                    // kan være null i Avro-klassen Periode.Svar.
                    periodeBekreftelse.inntektUnderveis == true,
                    periodeBekreftelse.fortsattArbeidssoker == true,
                ),
            )
        return bekreftelse
    }
}

data class BekreftelseMelding(
    val kafkaKey: Long,
    val periodeId: UUID,
    val fnr: String,
    val periodeStart: Instant,
    val periodeSlutt: Instant,
    val inntektUnderveis: Boolean?,
    val fortsattArbeidssoker: Boolean?,
)

const val ARBEIDSSOKERPERIODE_BEKREFTELSE_TOPIC =
    "paw.arbeidssoker-bekreftelse-friskmeldt-til-arbeidsformidling-v1"
