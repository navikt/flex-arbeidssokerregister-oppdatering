package no.nav.helse.flex.sykepengesoknad

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.helse.flex.objectMapper
import no.nav.helse.flex.serialisertTilString
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerRecord
import org.springframework.stereotype.Component
import java.util.*

@Component
class ArbeidssokerregisterPeriodeStoppProducer(
    private val kafkaProducer: Producer<String, String>,
) {
    fun send(stoppMelding: ArbeidssokerregisterPeriodeStoppMelding) {
        kafkaProducer.send(
            ProducerRecord(
                ARBEIDSSOKERREGISTER_PERIODE_STOPP_TOPIC,
                stoppMelding.fnr.asProducerRecordKey(),
                stoppMelding.serialisertTilString(),
            ),
        )
    }
}

data class ArbeidssokerregisterPeriodeStoppMelding(
    val id: String,
    val fnr: String,
)

internal fun String.asProducerRecordKey(): String = UUID.nameUUIDFromBytes(this.toByteArray()).toString()

internal fun String.tilArbeidssokerregisterPeriodeStoppMelding(): ArbeidssokerregisterPeriodeStoppMelding = objectMapper.readValue(this)

const val ARBEIDSSOKERREGISTER_PERIODE_STOPP_TOPIC = "flex.arbeidssokerregister-stopp-topic"
