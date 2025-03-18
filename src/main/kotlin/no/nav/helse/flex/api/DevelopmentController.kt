package no.nav.helse.flex.api

import no.nav.helse.flex.arbeidssokerregister.ArbeidssokerperiodeBekreftelseProducer
import no.nav.helse.flex.arbeidssokerregister.ArbeidssokerperiodePaaVegneAvProducer
import no.nav.helse.flex.arbeidssokerregister.ArbeidssokerperiodeRequest
import no.nav.helse.flex.arbeidssokerregister.ArbeidssokerregisterClient
import no.nav.helse.flex.arbeidssokerregister.BekreftelseMelding
import no.nav.helse.flex.arbeidssokerregister.KafkaKeyGeneratorClient
import no.nav.helse.flex.arbeidssokerregister.KafkaKeyGeneratorRequest
import no.nav.helse.flex.arbeidssokerregister.PaaVegneAvStartMelding
import no.nav.helse.flex.arbeidssokerregister.PaaVegneAvStoppMelding
import no.nav.helse.flex.logger
import no.nav.helse.flex.sykepengesoknad.ArbeidssokerperiodeStoppProducer
import no.nav.helse.flex.sykepengesoknad.SOKNAD_DEAKTIVERES_ETTER_MAANEDER
import no.nav.helse.flex.sykepengesoknad.StoppMelding
import no.nav.helse.flex.sykepengesoknad.beregnGraceMS
import no.nav.security.token.support.core.api.Unprotected
import org.springframework.context.annotation.Profile
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.*

@Profile("testdatareset")
@Unprotected
@RestController
@RequestMapping("/api/v1")
class DevelopmentController(
    private val arbeidssokerperiodeStoppProducer: ArbeidssokerperiodeStoppProducer,
    private val kafkaKeyGeneratorClient: KafkaKeyGeneratorClient,
    private val arbeidssokerregisterClient: ArbeidssokerregisterClient,
    private val paaVegneAvProducer: ArbeidssokerperiodePaaVegneAvProducer,
    private val bekrefelseProducer: ArbeidssokerperiodeBekreftelseProducer,
) {
    private val log = logger()

    @GetMapping("/arbeidssokerregisteret/kafka-key/{fnr}")
    fun hentKafkaKey(
        @PathVariable fnr: String,
    ): ResponseEntity<KafkaKeyResponse?> {
        kafkaKeyGeneratorClient.hentKafkaKey(KafkaKeyGeneratorRequest(fnr))!!.let {
            return ResponseEntity.ok(KafkaKeyResponse(it.key))
        }
    }

    @GetMapping("/arbeidssokerregisteret/periode/{fnr}")
    fun hentArbeidssokerperiode(
        @PathVariable fnr: String,
    ): ResponseEntity<ArbeidssokerperiodeResponse> {
        arbeidssokerregisterClient.hentSisteArbeidssokerperiode(ArbeidssokerperiodeRequest(fnr)).let {
            it.single().also {
                return ResponseEntity.ok(
                    ArbeidssokerperiodeResponse(
                        it.periodeId.toString(),
                        it.startet.utfoertAv.type,
                        it.avsluttet != null,
                    ),
                )
            }
        }
    }

    @PostMapping("/arbeidssokerregisteret/paa-vegne-av-start")
    fun sendPaaVegneAvStartMelding(
        @RequestBody request: PaaVegneAvRequest,
    ): ResponseEntity<Void> {
        paaVegneAvProducer.send(request.tilPaaVegneStartMelding())

        log.info("Sendt PaaVegneAvMelding for periodeId=${request.periodeId} fra DevelopmentController.")
        return ResponseEntity.ok().build()
    }

    @PostMapping("/arbeidssokerregisteret/paa-vegne-av-stopp")
    fun sendPaaVegneAvStoppMelding(
        @RequestBody request: PaaVegneAvRequest,
    ): ResponseEntity<Void> {
        paaVegneAvProducer.send(request.tilPaaVegneStoppMelding())

        log.info("Sendt PaaVegneAvStoppMelding for periodeId=${request.periodeId} fra DevelopmentController.")
        return ResponseEntity.ok().build()
    }

    @PostMapping("/arbeidssokerregisteret/bekreftelse")
    fun sendBekreftelseMelding(
        @RequestBody request: PeriodeBekrefelseRequest,
    ): ResponseEntity<Void> {
        bekrefelseProducer.send(request.tilBekreftelseMelding())

        log.info("Sendt PeriodeBekreftelse for periodeId: ${request.periodeId} fra DevelopmentController.")
        return ResponseEntity.ok().build()
    }

    @PostMapping("/sykepengesoknad/stopp-melding")
    fun sendArbeidssokerperiodeStoppMelding(
        @RequestBody request: StoppRequest,
    ): ResponseEntity<Void> {
        arbeidssokerperiodeStoppProducer.send(request.tilStoppMelding())

        log.info("Sendt StoppMelding med id: ${request.id} fra DevelopmentController.")
        return ResponseEntity.ok().build()
    }
}

private fun PaaVegneAvRequest.tilPaaVegneStartMelding() =
    PaaVegneAvStartMelding(
        this.kafkaKey,
        UUID.fromString(this.periodeId),
        beregnGraceMS(LocalDate.now(), SOKNAD_DEAKTIVERES_ETTER_MAANEDER),
    )

private fun PaaVegneAvRequest.tilPaaVegneStoppMelding() =
    PaaVegneAvStoppMelding(
        this.kafkaKey,
        UUID.fromString(this.periodeId),
    )

private fun PeriodeBekrefelseRequest.tilBekreftelseMelding() =
    BekreftelseMelding(
        kafkaKey = this.kafkaKey,
        periodeId = UUID.fromString(this.periodeId),
        fnr = this.fnr,
        periodeStart = Instant.now(),
        periodeSlutt = Instant.now().plus(14, ChronoUnit.DAYS),
        inntektUnderveis = this.inntektUnderveis,
        fortsattArbeidssoker = this.fortsattArbeidssoker,
    )

private fun StoppRequest.tilStoppMelding() =
    StoppMelding(
        vedtaksperiodeId = this.id,
        fnr = this.fnr,
        avsluttetTidspunkt = Instant.now(),
    )

data class StoppRequest(
    val id: String,
    val fnr: String,
)

data class PaaVegneAvRequest(
    val kafkaKey: Long,
    val periodeId: String,
    val fnr: String,
)

data class PeriodeBekrefelseRequest(
    val kafkaKey: Long,
    val periodeId: String,
    val fnr: String,
    val inntektUnderveis: Boolean,
    val fortsattArbeidssoker: Boolean,
)

data class KafkaKeyResponse(
    val kafkaKey: Long,
)

data class ArbeidssokerperiodeResponse(
    val periodeId: String,
    val utfoertAv: String,
    val erAvsluttet: Boolean,
)
