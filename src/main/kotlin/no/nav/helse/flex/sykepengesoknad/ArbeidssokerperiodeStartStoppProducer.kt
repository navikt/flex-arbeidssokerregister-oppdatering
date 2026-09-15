package no.nav.helse.flex.sykepengesoknad

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import io.opentelemetry.instrumentation.annotations.WithSpan
import no.nav.helse.flex.logger
import no.nav.helse.flex.objectMapper
import no.nav.helse.flex.serialisertTilString
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerRecord
import org.springframework.stereotype.Component
import tools.jackson.module.kotlin.readValue
import java.time.Instant
import java.util.*

@Component
class ArbeidssokerperiodeStartStoppProducer(
    private val kafkaProducer: Producer<String, String>,
) {
    private val log = logger()

    @WithSpan
    fun send(startStoppMelding: StartStoppMelding) {
        Span.current().addEvent(
            "StartStoppMelding",
            Attributes.of(
                AttributeKey.stringKey("vedtaksperiodeId"),
                startStoppMelding.vedtaksperiodeId,
                AttributeKey.stringKey("tidspunkt"),
                startStoppMelding.tidspunkt.toString(),
            ),
        )

        kafkaProducer.send(
            ProducerRecord(
                ARBEIDSSOKERPERIODE_START_STOPP_TOPIC,
                startStoppMelding.fnr.asProducerRecordKey(),
                startStoppMelding.serialisertTilString(),
            ),
        )

        log.info("Publisert StartStoppMelding for vedtaksperiode: ${startStoppMelding.vedtaksperiodeId}.")
    }
}

data class StartStoppMelding(
    val operation: StartStop,
    val vedtaksperiodeId: String,
    val fnr: String,
    val tidspunkt: Instant,
)

enum class StartStop {
    START,
    STOPP
}

internal fun String.asProducerRecordKey(): String = UUID.nameUUIDFromBytes(this.toByteArray()).toString()

internal fun String.tilArbeidssokerperiodeStartStoppMelding(): StartStoppMelding = objectMapper.readValue(this)

const val ARBEIDSSOKERPERIODE_START_STOPP_TOPIC = "flex.arbeidssokerregister-start-stopp-topic"
