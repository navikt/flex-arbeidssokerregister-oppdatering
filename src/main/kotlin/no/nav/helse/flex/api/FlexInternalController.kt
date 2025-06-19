package no.nav.helse.flex.api

import no.nav.helse.flex.api.OIDCIssuer.AZUREATOR
import no.nav.helse.flex.arbeidssokerperiode.Arbeidssokerperiode
import no.nav.helse.flex.arbeidssokerperiode.ArbeidssokerperiodeRepository
import no.nav.helse.flex.arbeidssokerperiode.AvsluttetAarsak
import no.nav.helse.flex.sykepengesoknad.Periodebekreftelse
import no.nav.helse.flex.sykepengesoknad.PeriodebekreftelseRepository
import no.nav.security.token.support.core.api.ProtectedWithClaims
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.time.LocalDate

@RestController
@ProtectedWithClaims(issuer = AZUREATOR)
@RequestMapping("/api/v1/flex")
class FlexInternalController(
    private val clientValidation: ClientValidation,
    private val arbeidssokerperiodeRepository: ArbeidssokerperiodeRepository,
    private val periodebekreftelseRepository: PeriodebekreftelseRepository,
) {
    @PostMapping("/arbeidssokerperioder")
    fun hentArbeidsokerperioder(
        @RequestBody request: FlexInternalRequest,
    ): ResponseEntity<FlexInternalResponse> {
        clientValidation.validateClientId(NamespaceAndApp(namespace = "flex", app = "flex-internal-frontend"))

        val arbeidssokerperioder =
            arbeidssokerperiodeRepository.findByFnr(request.fnr)?.map {
                it.tilArbeidssokerperiodeResponse(hentPeriodebekreftelser(it))
            } ?: emptyList()

        return ResponseEntity.ok(FlexInternalResponse(arbeidssokerperioder))
    }

    @PutMapping("/arbeidssokerperioder/oppdater-tom")
    fun updateVedtaksperiodeTom(
        @RequestBody request: OppdatertVedtaksperiodeTomRequest,
    ): ResponseEntity<Void> {
        clientValidation.validateClientId(NamespaceAndApp(namespace = "flex", app = "flex-internal-frontend"))

        val arbeidssokerperiode =
            arbeidssokerperiodeRepository
                .findById(request.id)
                .orElse(null) ?: return ResponseEntity.notFound().build()

        arbeidssokerperiode
            .copy(
                vedtaksperiodeTom = request.vedtaksperiodeTom,
            ).also {
                arbeidssokerperiodeRepository.save(it)
            }

        return ResponseEntity.noContent().build()
    }

    @PutMapping("/arbeidssokerperioder/oppdater-arbeidssokerperiode-id")
    fun updateArbeidssokerperiodeId(
        @RequestBody request: OppdaterArbeidssokerperiodeIdRequest,
    ): ResponseEntity<Void> {
        clientValidation.validateClientId(NamespaceAndApp(namespace = "flex", app = "flex-internal-frontend"))

        val arbeidssokerperiode =
            arbeidssokerperiodeRepository
                .findById(request.id)
                .orElse(null) ?: return ResponseEntity.notFound().build()

        arbeidssokerperiode
            .copy(
                arbeidssokerperiodeId = request.arbeidssokerperiodeId,
            ).also {
                arbeidssokerperiodeRepository.save(it)
            }

        return ResponseEntity.noContent().build()
    }

    private fun hentPeriodebekreftelser(arbeidssokerperiode: Arbeidssokerperiode): List<PeriodebekreftelseResponse> =
        periodebekreftelseRepository
            .findByArbeidssokerperiodeId(
                arbeidssokerperiode.id!!,
            ).map { it.tilPeriodebekreftelseResponse() }
}

data class FlexInternalResponse(
    val arbeidssokerperioder: List<ArbeidssokerperiodeResponse>,
)

data class FlexInternalRequest(
    val fnr: String,
)

data class OppdatertVedtaksperiodeTomRequest(
    val id: String,
    val vedtaksperiodeTom: LocalDate,
)

data class OppdaterArbeidssokerperiodeIdRequest(
    val id: String,
    val arbeidssokerperiodeId: String,
)

fun Arbeidssokerperiode.tilArbeidssokerperiodeResponse(periodebekreftelser: List<PeriodebekreftelseResponse>): ArbeidssokerperiodeResponse =
    ArbeidssokerperiodeResponse(
        id = this.id!!,
        fnr = this.fnr,
        vedtaksperiodeId = this.vedtaksperiodeId,
        vedtaksperiodeFom = this.vedtaksperiodeFom,
        vedtaksperiodeTom = this.vedtaksperiodeTom,
        opprettet = this.opprettet,
        arbeidssokerperiodeId = this.arbeidssokerperiodeId,
        sendtPaaVegneAv = this.sendtPaaVegneAv,
        avsluttetMottatt = this.avsluttetMottatt,
        avsluttetTidspunkt = this.avsluttetTidspunkt,
        sendtAvsluttet = this.sendtAvsluttet,
        avsluttetAarsak = this.avsluttetAarsak,
        periodebekreftelser = periodebekreftelser,
    )

data class ArbeidssokerperiodeResponse(
    val id: String,
    val fnr: String,
    val vedtaksperiodeId: String,
    val vedtaksperiodeFom: LocalDate,
    val vedtaksperiodeTom: LocalDate,
    val opprettet: Instant,
    val arbeidssokerperiodeId: String? = null,
    val sendtPaaVegneAv: Instant? = null,
    val avsluttetMottatt: Instant? = null,
    val avsluttetTidspunkt: Instant? = null,
    val sendtAvsluttet: Instant? = null,
    val avsluttetAarsak: AvsluttetAarsak? = null,
    val periodebekreftelser: List<PeriodebekreftelseResponse>? = emptyList(),
)

fun Periodebekreftelse.tilPeriodebekreftelseResponse(): PeriodebekreftelseResponse =
    PeriodebekreftelseResponse(
        id = this.id!!,
        arbeidssokerperiodeId = this.arbeidssokerperiodeId,
        sykepengesoknadId = this.sykepengesoknadId,
        fortsattArbeidssoker = this.fortsattArbeidssoker,
        inntektUnderveis = this.inntektUnderveis,
        opprettet = this.opprettet,
        avsluttendeSoknad = this.avsluttendeSoknad,
    )

data class PeriodebekreftelseResponse(
    val id: String,
    val arbeidssokerperiodeId: String,
    val sykepengesoknadId: String,
    val fortsattArbeidssoker: Boolean?,
    val inntektUnderveis: Boolean?,
    val opprettet: Instant,
    val avsluttendeSoknad: Boolean = false,
)
