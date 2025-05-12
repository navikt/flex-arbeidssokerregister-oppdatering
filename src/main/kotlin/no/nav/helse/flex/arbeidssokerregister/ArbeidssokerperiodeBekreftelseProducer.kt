package no.nav.helse.flex.arbeidssokerregister

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import io.opentelemetry.instrumentation.annotations.WithSpan
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

    @WithSpan
    fun send(bekreftelseMelding: BekreftelseMelding) {
        val kafkaKey = bekreftelseMelding.kafkaKey

        Span.current().addEvent(
            "BekreftelseMelding",
            Attributes.of(
                AttributeKey.stringKey("periodeId"),
                bekreftelseMelding.arbeidssokerregisterPeriodeId.toString(),
                AttributeKey.stringKey("fortsattArbeidssoker"),
                bekreftelseMelding.fortsattArbeidssoker.toString(),
                AttributeKey.stringKey("inntektUnderveis"),
                bekreftelseMelding.inntektUnderveis.toString(),
                AttributeKey.stringKey("periodeStart"),
                bekreftelseMelding.periodeStart.toString(),
                AttributeKey.stringKey("periodeSlutt"),
                bekreftelseMelding.periodeSlutt.toString(),
            ),
        )

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
                "inntektUnderveis: ${bekreftelseMelding.inntektUnderveis} for " +
                "arbeidssøkerperiode: ${bekreftelseMelding.arbeidssokerperiodeId} " +
                "og periode i arbeidssøkerregisteret: ${bekreftelseMelding.arbeidssokerregisterPeriodeId}. Periode er " +
                "fra: ${bekreftelseMelding.periodeStart} " +
                "til: ${bekreftelseMelding.periodeSlutt}.",
        )
    }

    private fun lagBekreftelse(periodeBekreftelse: BekreftelseMelding): Bekreftelse {
        val bruker =
            Bruker().also {
                it.type = BrukerType.SLUTTBRUKER
                it.id = periodeBekreftelse.fnr
            }
        val metadata =
            Metadata(Instant.now(), bruker, UTFOERT_AV, AARSAK)

        val bekreftelse =
            Bekreftelse(
                UUID.fromString(periodeBekreftelse.arbeidssokerregisterPeriodeId),
                Bekreftelsesloesning.FRISKMELDT_TIL_ARBEIDSFORMIDLING,
                UUID.randomUUID(),
                Svar(
                    metadata,
                    periodeBekreftelse.periodeStart,
                    periodeBekreftelse.periodeSlutt,
                    // "inntektUnderveis" og "fortsattArbeidssoker" er null hvis det er siste søknad i en periode. Da
                    // sendes det ikke Periodebekreftelse. Feltene kan ikke være null i Avro-klassen Periode.svar,
                    // men kan settes til false,
                    periodeBekreftelse.inntektUnderveis == true,
                    periodeBekreftelse.fortsattArbeidssoker == true,
                ),
            )
        return bekreftelse
    }
}

data class BekreftelseMelding(
    val kafkaKey: Long,
    val arbeidssokerperiodeId: String,
    val arbeidssokerregisterPeriodeId: String,
    val fnr: String,
    val periodeStart: Instant,
    val periodeSlutt: Instant,
    val inntektUnderveis: Boolean?,
    val fortsattArbeidssoker: Boolean?,
)

const val ARBEIDSSOKERPERIODE_BEKREFTELSE_TOPIC =
    "paw.arbeidssoker-bekreftelse-friskmeldt-til-arbeidsformidling-v1"
