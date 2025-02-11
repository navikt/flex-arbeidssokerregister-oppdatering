package no.nav.helse.flex.arbeidssoker

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.helse.flex.objectMapper
import no.nav.helse.flex.serialisertTilString
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerRecord
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class ArbeidssokerRegisterStoppProducer(
    private val kafkaProducer: Producer<String, String>,
) {
    fun send(arbeidssokerRegisterStopp: ArbeidssokerRegisterStopp) {
        kafkaProducer.send(
            ProducerRecord(
                ARBEIDSSOKERREGISTER_STOPP_TOPIC,
                arbeidssokerRegisterStopp.fnr.asProducerRecordKey(),
                arbeidssokerRegisterStopp.serialisertTilString(),
            ),
        )
    }
}

data class ArbeidssokerRegisterStopp(
    val id: String,
    val fnr: String,
)

internal fun String.asProducerRecordKey(): String = UUID.nameUUIDFromBytes(this.toByteArray()).toString()

internal fun String.tilArbeidssokerRegisterStopp(): ArbeidssokerRegisterStopp = objectMapper.readValue(this)

const val ARBEIDSSOKERREGISTER_STOPP_TOPIC = "flex.arbeidssokerregister-stopp-topic"
