package no.nav.helse.flex.sykepengesoknad

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.helse.flex.EnvironmentToggles
import no.nav.helse.flex.arbeidssokerperiode.Arbeidssokerperiode
import no.nav.helse.flex.arbeidssokerperiode.ArbeidssokerperiodeRepository
import no.nav.helse.flex.arbeidssokerperiode.AvsluttetAarsak
import no.nav.helse.flex.arbeidssokerregister.*
import no.nav.helse.flex.logger
import no.nav.helse.flex.objectMapper
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadsstatusDTO
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadstypeDTO
import no.nav.helse.flex.sykepengesoknad.kafka.SykepengesoknadDTO
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.*
import java.util.concurrent.TimeUnit

const val SOKNAD_DEAKTIVERES_ETTER_MAANEDER = 4

@Service
class SykepengesoknadService(
    private val kafkaKeyGeneratorClient: KafkaKeyGeneratorClient,
    private val arbeidssokerregisterClient: ArbeidssokerregisterClient,
    private val arbeidssokerperiodeRepository: ArbeidssokerperiodeRepository,
    private val periodebekreftelseRepository: PeriodebekreftelseRepository,
    private val paaVegneAvProducer: ArbeidssokerperiodePaaVegneAvProducer,
    private val bekreftelseProducer: ArbeidssokerperiodeBekreftelseProducer,
    private val vedtaksperiodeExceptionRepository: VedtaksperiodeExceptionRepository,
    private val environmentToggles: EnvironmentToggles,
) {
    private val log = logger()

    @Scheduled(initialDelay = 2, fixedDelay = 3600, timeUnit = TimeUnit.MINUTES)
    fun restorePaaVegneAv() {
        if (environmentToggles.erProduksjon()) {
            listOf(
                "62a94391-b3bc-4bc9-bd3c-ddf38cfa5412",
                "d654ea16-a72f-440c-ac45-4960f8ee0232",
                "decc0f76-1c39-4025-95e7-8331983ce46c",
                "2a8a3024-2a17-4ba1-8888-4da0992f4591",
            ).forEach {
                arbeidssokerperiodeRepository.findByVedtaksperiodeId(it)?.let {
                    val arbeidsokerperiodeResponse =
                        hentArbeidssokerperiodeId(it.fnr).singleOrNull()
                            ?: throw ArbeidssokerperiodeException(
                                "Fant ikke arbeidssøkerperiode for vedtaksperiode: ${it.vedtaksperiodeId}.",
                            )

                    paaVegneAvProducer.send(
                        PaaVegneAvStartMelding(
                            it.kafkaRecordKey!!,
                            UUID.fromString(arbeidsokerperiodeResponse.periodeId),
                            beregnGraceMS(it.vedtaksperiodeTom, SOKNAD_DEAKTIVERES_ETTER_MAANEDER),
                        ),
                    )

                    arbeidssokerperiodeRepository.save(
                        it.copy(
                            avsluttetMottatt = null,
                            avsluttetTidspunkt = null,
                            arbeidssokerperiodeId = arbeidsokerperiodeResponse.periodeId,
                            sendtPaaVegneAv = Instant.now(),
                        ),
                    )

                    log.info(
                        "Slettet avsluttet og sendt ny PaaVegneAv.Start.melding for vedtaksperiode: ${it.vedtaksperiodeId} " +
                            "med arbeidssokerperiodeId: ${it.arbeidssokerperiodeId}.",
                    )
                }
            }
        }
    }

    @Transactional
    fun behandleSoknad(soknad: SykepengesoknadDTO) {
        when {
            soknad.erFremtidigFriskTilArbeidSoknad() -> behandleVedtaksperiode(soknad)

            soknad.erSendtFriskTilArbeidSoknad() -> behandleBekreftelse(soknad)
        }
    }

    private fun behandleVedtaksperiode(soknad: SykepengesoknadDTO) {
        if (soknad.ignorerArbeidssokerregister == true) {
            log.info(
                "Ignorerer søknad: ${soknad.id} med vedtaksperiodeId: ${soknad.friskTilArbeidVedtakId} " +
                    "siden ignorerArbeidssokerregister er satt.",
            )
            return
        }

        val vedtaksperiode = soknad.tilVedtaksperiode()

        if (!erNyVedtaksperiode(vedtaksperiode)) {
            return
        }

        val kafkaRecordKey = hentKafkaRecordKey(vedtaksperiode.fnr)

        // Arbeidssøkerregisteret returnerer tom liste hvis bruker ikke er registrert.
        val arbeidsokerperiodeResponse =
            hentArbeidssokerperiodeId(vedtaksperiode.fnr).singleOrNull()
                ?: throw ArbeidssokerperiodeException(
                    "Fant ikke arbeidssøkerperiode for søknad: ${vedtaksperiode.sykepengesoknadId} med " +
                        "vedtaksperiode: ${vedtaksperiode.vedtaksperiodeId}.",
                )

        if (arbeidsokerperiodeResponse.avsluttet != null) {
            val avsluttetTidspunkt = arbeidsokerperiodeResponse.avsluttet.tidspunkt.toLocalDate()
            throw ArbeidssokerperiodeException(
                "Arbeidssøkerperiode i arbeidssøkerregisteret: ${arbeidsokerperiodeResponse.periodeId} for " +
                    "søknad: ${vedtaksperiode.sykepengesoknadId} med " +
                    "vedtaksperiode: ${vedtaksperiode.vedtaksperiodeId} ble " +
                    "avsluttet $avsluttetTidspunkt.",
            )
        }

        val lagretArbeidssokerperiode =
            arbeidssokerperiodeRepository.save(
                vedtaksperiode.toArbeidssokerperiode(
                    kafkaRecordKey,
                    arbeidsokerperiodeResponse.periodeId,
                    Instant.now(),
                ),
            )

        paaVegneAvProducer.send(
            PaaVegneAvStartMelding(
                kafkaRecordKey,
                UUID.fromString(arbeidsokerperiodeResponse.periodeId),
                beregnGraceMS(vedtaksperiode.tom, SOKNAD_DEAKTIVERES_ETTER_MAANEDER),
            ),
        )

        log.info(
            "Opprettet arbeidssøkerperiode: ${lagretArbeidssokerperiode.id} for " +
                "søknad: ${vedtaksperiode.sykepengesoknadId} med " +
                "vedtaksperiode: ${vedtaksperiode.vedtaksperiodeId} og " +
                "periode i arbeidssøkerregisteret: ${arbeidsokerperiodeResponse.periodeId}.",
        )
    }

    private fun behandleBekreftelse(soknad: SykepengesoknadDTO) {
        if (soknad.ignorerArbeidssokerregister == true) {
            log.info(
                "Ignorerer periodebekreftelse for søknad: ${soknad.id} med vedtaksperiodeId: ${soknad.friskTilArbeidVedtakId} siden ignorerArbeidssokerregisterer satt.",
            )
            return
        }

        if (soknad.korrigerer != null) {
            log.info("Ignorerer periodebekreftelse for korrigerende søknad: ${soknad.id}.")
            return
        }

        val arbeidssokerperiode =
            arbeidssokerperiodeRepository.findByVedtaksperiodeId(soknad.friskTilArbeidVedtakId!!)

        if (arbeidssokerperiode == null) {
            throw PeriodebekreftelseException(
                "Fant ikke arbeidssøkerperiode for søknad: ${soknad.id} med " +
                    "vedtaksperiode: ${soknad.friskTilArbeidVedtakId}.",
            )
        }

        if (periodebekreftelseRepository.findBySykepengesoknadId(soknad.id) != null) {
            return
        }

        val erAvsluttendeSoknad = arbeidssokerperiode.vedtaksperiodeTom == soknad.tom

        if (!erAvsluttendeSoknad) {
            if (soknad.fortsattArbeidssoker == null) {
                throw PeriodebekreftelseException(
                    "Mangler verdi for fortsattArbeidssoker i søknad: ${soknad.id} med " +
                        "vedtaksperiode: ${soknad.friskTilArbeidVedtakId} og " +
                        "arbeidssokerperiode: ${arbeidssokerperiode.id} som skal være satt da søknaden " +
                        "ikke er siste i perioden.",
                )
            }
        }

        periodebekreftelseRepository.save(
            Periodebekreftelse(
                arbeidssokerperiodeId = arbeidssokerperiode.id!!,
                sykepengesoknadId = soknad.id,
                fortsattArbeidssoker = soknad.fortsattArbeidssoker,
                inntektUnderveis = soknad.inntektUnderveis,
                opprettet = Instant.now(),
                avsluttendeSoknad = erAvsluttendeSoknad,
            ),
        )

        if (erAvsluttendeSoknad) {
            arbeidssokerperiode.lagreAvsluttetAarsak(AvsluttetAarsak.AVSLUTTET_PERIODE)
            sendPaaVegneAvStoppMelding(arbeidssokerperiode)
        } else {
            if (soknad.fortsattArbeidssoker == false) {
                arbeidssokerperiode.lagreAvsluttetAarsak(AvsluttetAarsak.BRUKER)
            }
            sendBekreftelseMelding(arbeidssokerperiode, soknad)
        }

        log.info(
            "Behandlet periodebekreftelse for søknad: ${soknad.id} med " +
                "vedtaksperiode: ${soknad.friskTilArbeidVedtakId}, " +
                "arbeidssøkerperiode: ${arbeidssokerperiode.id} og periode i " +
                "arbeidssøkerregisteret: ${arbeidssokerperiode.arbeidssokerperiodeId}.",
        )
    }

    private fun sendBekreftelseMelding(
        arbeidssokerperiode: Arbeidssokerperiode,
        sykepengesoknadDTO: SykepengesoknadDTO,
    ) {
        val bekreftelseMelding =
            BekreftelseMelding(
                kafkaKey = arbeidssokerperiode.kafkaRecordKey!!,
                periodeId = UUID.fromString(arbeidssokerperiode.arbeidssokerperiodeId!!),
                fnr = sykepengesoknadDTO.fnr,
                periodeStart = sykepengesoknadDTO.fom!!.toInstantAtStartOfDay(),
                periodeSlutt = sykepengesoknadDTO.tom!!.plusDays(1).toInstantAtStartOfDay(),
                inntektUnderveis = sykepengesoknadDTO.inntektUnderveis,
                fortsattArbeidssoker = sykepengesoknadDTO.fortsattArbeidssoker,
            )
        bekreftelseProducer.send(bekreftelseMelding)
    }

    private fun lagreException(
        sykepengesoknadDTO: SykepengesoknadDTO,
        e: ArbeidssokerperiodeException,
    ) {
        vedtaksperiodeExceptionRepository.save(
            VedtaksperiodeException(
                opprettet = Instant.now(),
                vedtaksperiodeId = sykepengesoknadDTO.friskTilArbeidVedtakId!!,
                sykepengesoknadId = sykepengesoknadDTO.id,
                fnr = sykepengesoknadDTO.fnr,
                exceptionClassName = e.javaClass.canonicalName,
                exceptionMessage = e.message,
            ),
        )
    }

    private fun sendPaaVegneAvStoppMelding(arbeidssokerperiode: Arbeidssokerperiode) {
        val paaVegneAvMelding =
            PaaVegneAvStoppMelding(
                kafkaKey = arbeidssokerperiode.kafkaRecordKey!!,
                periodeId = UUID.fromString(arbeidssokerperiode.arbeidssokerperiodeId),
            )

        paaVegneAvProducer.send(paaVegneAvMelding)
    }

    private fun erNyVedtaksperiode(vedtaksperiode: FriskTilArbeidVedtaksperiode) =
        arbeidssokerperiodeRepository.findByVedtaksperiodeId(vedtaksperiode.vedtaksperiodeId) == null

    private fun hentKafkaRecordKey(fnr: String): Long = kafkaKeyGeneratorClient.hentKafkaKey(KafkaKeyGeneratorRequest(fnr))!!.key

    private fun hentArbeidssokerperiodeId(fnr: String): List<ArbeidssokerperiodeResponse> =
        arbeidssokerregisterClient.hentSisteArbeidssokerperiode(ArbeidssokerperiodeRequest(fnr))

    private fun SykepengesoknadDTO.erFremtidigFriskTilArbeidSoknad() =
        type == SoknadstypeDTO.FRISKMELDT_TIL_ARBEIDSFORMIDLING && status == SoknadsstatusDTO.FREMTIDIG

    private fun SykepengesoknadDTO.erSendtFriskTilArbeidSoknad() =
        type == SoknadstypeDTO.FRISKMELDT_TIL_ARBEIDSFORMIDLING && status == SoknadsstatusDTO.SENDT

    private fun Arbeidssokerperiode.lagreAvsluttetAarsak(avsluttetAarsak: AvsluttetAarsak) {
        arbeidssokerperiodeRepository.save(
            this.copy(
                sendtAvsluttet = Instant.now(),
                avsluttetAarsak = avsluttetAarsak,
            ),
        )
    }

    fun FriskTilArbeidVedtaksperiode.toArbeidssokerperiode(
        kafkaRecordKey: Long,
        arbeidssokerperiodeId: String,
        sendtPaaVegneAv: Instant,
    ) = Arbeidssokerperiode(
        fnr = this.fnr,
        vedtaksperiodeId = this.vedtaksperiodeId,
        vedtaksperiodeFom = this.fom,
        vedtaksperiodeTom = this.tom,
        opprettet = Instant.now(),
        kafkaRecordKey = kafkaRecordKey,
        arbeidssokerperiodeId = arbeidssokerperiodeId,
        sendtPaaVegneAv = sendtPaaVegneAv,
    )

    fun SykepengesoknadDTO.tilVedtaksperiode(): FriskTilArbeidVedtaksperiode {
        val periode = this.friskTilArbeidVedtakPeriode!!.tilPeriode()
        return FriskTilArbeidVedtaksperiode(
            fnr = this.fnr,
            vedtaksperiodeId = this.friskTilArbeidVedtakId!!,
            sykepengesoknadId = this.id,
            fom = periode.fom,
            tom = periode.tom,
        )
    }
}

fun LocalDate.toInstantAtStartOfDay(): Instant = this.atStartOfDay().toInstant(ZoneOffset.UTC)

fun Instant.toLocalDate(): LocalDate = this.atZone(ZoneId.systemDefault()).toLocalDate()

fun String.tilPeriode(): Periode = objectMapper.readValue(this)

data class FriskTilArbeidVedtaksperiode(
    val fnr: String,
    val vedtaksperiodeId: String,
    val sykepengesoknadId: String,
    val fom: LocalDate,
    val tom: LocalDate,
)

data class Periode(
    val fom: LocalDate,
    val tom: LocalDate,
)

class ArbeidssokerperiodeException(
    message: String,
) : RuntimeException(message)

class PeriodebekreftelseException(
    message: String,
) : RuntimeException(message)

// Beregner antall millisekunder mellom dagen etter `tom` og `months` måneder senere.
fun beregnGraceMS(
    tom: LocalDate,
    months: Int,
): Long {
    val starttidspunkt = tom.plusDays(1).atStartOfDay()
    val sluttidspunkt = starttidspunkt.plusMonths(months.toLong())
    return Duration.between(starttidspunkt, sluttidspunkt).toMillis()
}
