package no.nav.helse.flex.api

import no.nav.helse.flex.arbeidssoker.ArbeidssokerregisterStoppMelding
import no.nav.helse.flex.arbeidssoker.ArbeidssokerregisterStoppProducer
import no.nav.helse.flex.paw.ArbeidssokerperiodeRequest
import no.nav.helse.flex.paw.ArbeidssokerregisterClient
import no.nav.helse.flex.paw.KafkaKeyGeneratorClient
import no.nav.helse.flex.paw.KafkaKeyGeneratorRequest
import no.nav.security.token.support.core.api.Unprotected
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseBody
import java.time.OffsetDateTime
import java.util.*

@Profile("testdatareset")
@Unprotected
@Controller
@RequestMapping("/api/v1")
class DevelopmentController(
    private val arbeidssokerregisterStoppProducer: ArbeidssokerregisterStoppProducer,
    private val kafkaKeyGeneratorClient: KafkaKeyGeneratorClient,
    private val arbeidssokerregisterClient: ArbeidssokerregisterClient,
) {
    @PostMapping("/arbeidssokerregister-stopp-melding")
    @ResponseBody
    fun sendArbeidssokerregisterStoppMelding(fnr: String): DevelopmentResponse {
        val id = UUID.randomUUID().toString()
        arbeidssokerregisterStoppProducer.send(ArbeidssokerregisterStoppMelding(id = id, fnr = fnr))
        return DevelopmentResponse("id=$id")
    }

    @GetMapping("/kafka-key/{fnr}")
    @ResponseBody
    fun hentKafkaKey(
        @PathVariable fnr: String,
    ): DevelopmentResponse {
        kafkaKeyGeneratorClient.hentKafkaKey(KafkaKeyGeneratorRequest(fnr))!!.let {
            return DevelopmentResponse("$it")
        }
    }

    @GetMapping("/arbeidssokerperiode/{fnr}")
    @ResponseBody
    fun hentArbeidssokerperiode(
        @PathVariable fnr: String,
    ): DevelopmentResponse {
        arbeidssokerregisterClient.hentSisteArbeidssokerperiode(ArbeidssokerperiodeRequest(fnr)).let {
            it.first().also {
                val response =
                    "periodeId: ${it.periodeId}, startet: ${it.startet.tidspunkt}, avsluttet: ${it.avsluttet != null}"
                return DevelopmentResponse(response)
            }
        }
    }
}

data class PeriodeResponse(
    val id: String,
    val startet: OffsetDateTime,
    val avsluttet: Boolean,
)

data class DevelopmentResponse(
    val message: String,
)
