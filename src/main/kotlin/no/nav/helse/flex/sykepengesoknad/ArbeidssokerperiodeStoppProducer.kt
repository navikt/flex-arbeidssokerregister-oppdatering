package no.nav.helse.flex.sykepengesoknad

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.helse.flex.logger
import no.nav.helse.flex.objectMapper
import no.nav.helse.flex.serialisertTilString
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerRecord
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.*

@Component
class ArbeidssokerperiodeStoppProducer(
    private val kafkaProducer: Producer<String, String>,
) {
    private val log = logger()

    fun send(stoppMelding: StoppMelding) {
        kafkaProducer.send(
            ProducerRecord(
                ARBEIDSSOKERPERIODE_STOPP_TOPIC,
                stoppMelding.fnr.asProducerRecordKey(),
                stoppMelding.serialisertTilString(),
            ),
        )

        log.info("Publisert StoppMelding for vedtaksperiode: ${stoppMelding.vedtaksperiodeId}.")
    }
}

data class StoppMelding(
    val vedtaksperiodeId: String,
    val fnr: String,
    val avsluttetTidspunkt: Instant,
)

internal fun String.asProducerRecordKey(): String = UUID.nameUUIDFromBytes(this.toByteArray()).toString()

internal fun String.tilArbeidssokerperiodeStoppMelding(): StoppMelding = objectMapper.readValue(this)

const val ARBEIDSSOKERPERIODE_STOPP_TOPIC = "flex.arbeidssokerregister-stopp-topic"
