package no.nav.helse.flex.sykepengesoknad

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.helse.flex.arbeidssokerperiode.Arbeidssokerperiode
import no.nav.helse.flex.arbeidssokerperiode.ArbeidssokerperiodeRepository
import no.nav.helse.flex.arbeidssokerperiode.AvsluttetAarsak
import no.nav.helse.flex.arbeidssokerregister.*
import no.nav.helse.flex.logger
import no.nav.helse.flex.objectMapper
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadsstatusDTO
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadstypeDTO
import no.nav.helse.flex.sykepengesoknad.kafka.SykepengesoknadDTO
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.*

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
) {
    private val log = logger()

    @Transactional
    fun behandleSoknad(sykepengesoknadDTO: SykepengesoknadDTO) {
        when {
            sykepengesoknadDTO.erFremtidigFriskTilArbeidSoknad() -> behandleVedtaksperiode(sykepengesoknadDTO)

            sykepengesoknadDTO.erSendtFriskTilArbeidSoknad() -> behandleBekreftelse(sykepengesoknadDTO)
        }
    }

    private fun behandleVedtaksperiode(sykepengesoknadDTO: SykepengesoknadDTO) {
        try {
            val vedtaksperiode = sykepengesoknadDTO.tilVedtaksperiode()
            if (!erNyVedtaksperiode(vedtaksperiode)) {
                log.warn(
                    "Ignorerer vedtaksperiode for søknad: ${sykepengesoknadDTO.id} med " +
                        "vedtaksperiode: ${sykepengesoknadDTO.friskTilArbeidVedtakId} da den allerede er behandlet.",
                )
                return
            }
            behandleVedtaksperiode(vedtaksperiode)
        } catch (e: ArbeidssokerperiodeException) {
            lagreException(sykepengesoknadDTO, e)
        }
    }

    private fun behandleVedtaksperiode(vedtaksperiode: FriskTilArbeidVedtaksperiode) {
        val kafkaRecordKey = hentKafkaRecordKey(vedtaksperiode.fnr)

        // Arbeidssøkerregisteret returnerer tom list hvis bruker ikke er registrert.
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

    private fun behandleBekreftelse(sykepengesoknadDTO: SykepengesoknadDTO) {
        if (sykepengesoknadDTO.korrigerer != null) {
            log.info("Ignorerer periodebekreftelse for korrigerende søknad: ${sykepengesoknadDTO.id}.")
            return
        }

        val arbeidssokerperiode =
            arbeidssokerperiodeRepository.findByVedtaksperiodeId(sykepengesoknadDTO.friskTilArbeidVedtakId!!)

        if (arbeidssokerperiode == null) {
            throw PeriodebekreftelseException(
                "Fant ikke arbeidssøkerperiode for søknad: ${sykepengesoknadDTO.id} med " +
                    "vedtaksperiode: ${sykepengesoknadDTO.friskTilArbeidVedtakId}.",
            )
        }

        if (periodebekreftelseRepository.findBySykepengesoknadId(sykepengesoknadDTO.id) != null) {
            log.warn("Ignorerer periodebekreftelse for søknad: ${sykepengesoknadDTO.id} da den allerede er behandlet.")
            return
        }

        val erAvsluttendeSoknad = arbeidssokerperiode.vedtaksperiodeTom == sykepengesoknadDTO.tom

        if (!erAvsluttendeSoknad) {
            if (sykepengesoknadDTO.fortsattArbeidssoker == null) {
                throw PeriodebekreftelseException(
                    "Mangler verdi for fortsattArbeidssoker i søknad: ${sykepengesoknadDTO.id} med " +
                        "vedtaksperiode: ${sykepengesoknadDTO.friskTilArbeidVedtakId} og " +
                        "arbeidssokerperiode: ${arbeidssokerperiode.id} som skal være satt da søknaden " +
                        "ikke er siste i perioden.",
                )
            }
        }

        periodebekreftelseRepository.save(
            Periodebekreftelse(
                arbeidssokerperiodeId = arbeidssokerperiode.id!!,
                sykepengesoknadId = sykepengesoknadDTO.id,
                fortsattArbeidssoker = sykepengesoknadDTO.fortsattArbeidssoker,
                inntektUnderveis = sykepengesoknadDTO.inntektUnderveis,
                opprettet = Instant.now(),
                avsluttendeSoknad = erAvsluttendeSoknad,
            ),
        )

        if (erAvsluttendeSoknad) {
            arbeidssokerperiode.lagreAvsluttetAarsak(AvsluttetAarsak.AVSLUTTET_PERIODE)
            sendPaaVegneAvStoppMelding(arbeidssokerperiode)
        } else {
            if (sykepengesoknadDTO.fortsattArbeidssoker == false) {
                arbeidssokerperiode.lagreAvsluttetAarsak(AvsluttetAarsak.BRUKER)
            }
            sendBekreftelseMelding(arbeidssokerperiode, sykepengesoknadDTO)
        }

        log.info(
            "Behandlet periodebekreftelse for søknad: ${sykepengesoknadDTO.id} med " +
                "vedtaksperiode: ${sykepengesoknadDTO.friskTilArbeidVedtakId}, " +
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
