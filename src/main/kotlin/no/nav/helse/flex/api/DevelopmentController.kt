package no.nav.helse.flex.api

import no.nav.helse.flex.arbeidssokerregister.ArbeidssokerperiodeRequest
import no.nav.helse.flex.arbeidssokerregister.ArbeidssokerregisterClient
import no.nav.helse.flex.arbeidssokerregister.KafkaKeyGeneratorClient
import no.nav.helse.flex.arbeidssokerregister.KafkaKeyGeneratorRequest
import no.nav.helse.flex.logger
import no.nav.helse.flex.sykepengesoknad.ArbeidssokerregisterPeriodeStoppMelding
import no.nav.helse.flex.sykepengesoknad.ArbeidssokerregisterPeriodeStoppProducer
import no.nav.security.token.support.core.api.Unprotected
import org.springframework.context.annotation.Profile
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Profile("testdatareset")
@Unprotected
@RestController
@RequestMapping("/api/v1")
class DevelopmentController(
    private val arbeidssokerregisterStoppProducer: ArbeidssokerregisterPeriodeStoppProducer,
    private val kafkaKeyGeneratorClient: KafkaKeyGeneratorClient,
    private val arbeidssokerregisterClient: ArbeidssokerregisterClient,
) {
    private val log = logger()

    @GetMapping("/arbeidssokerregisteret/kafka-key/{fnr}")
    fun hentKafkaKey(
        @PathVariable fnr: String,
    ): ResponseEntity<DevelopmentResponse?> {
        kafkaKeyGeneratorClient.hentKafkaKey(KafkaKeyGeneratorRequest(fnr))!!.let {
            return ResponseEntity.ok(DevelopmentResponse("kafkaKey=$it"))
        }
    }

    @GetMapping("/arbeidssokerregisteret/periode/{fnr}")
    fun hentArbeidssokerperiode(
        @PathVariable fnr: String,
    ): ResponseEntity<DevelopmentResponse> {
        arbeidssokerregisterClient.hentSisteArbeidssokerperiode(ArbeidssokerperiodeRequest(fnr)).let {
            it.first().also {
                val erAvsluttet = it.avsluttet != null
                val type = it.startet.utfoertAv.type
                return ResponseEntity.ok(
                    DevelopmentResponse("periodeId: ${it.periodeId}, utfoertAv: $type, erAvsluttet: $erAvsluttet"),
                )
            }
        }
    }

    @PostMapping("/sykepengesoknad/stopp-melding")
    fun sendArbeidssokerregisterStoppMelding(
        @RequestBody request: StoppRequest,
    ): ResponseEntity<Void> {
        arbeidssokerregisterStoppProducer.send(request.tilStoppMelding())

        log.info("Sendt ArbeidssokerregisterStoppMelding med id: ${request.id}")
        return ResponseEntity.ok().build()
    }
}

private fun StoppRequest.tilStoppMelding() =
    ArbeidssokerregisterPeriodeStoppMelding(
        id = this.id,
        fnr = this.fnr,
    )

data class StoppRequest(
    val id: String,
    val fnr: String,
)

data class DevelopmentResponse(
    val message: String,
)
