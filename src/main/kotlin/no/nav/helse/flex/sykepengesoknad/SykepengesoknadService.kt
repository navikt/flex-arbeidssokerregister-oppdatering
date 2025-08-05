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

const val SOKNAD_DEAKTIVERES_ETTER_MAANEDER = 4

@Service
class SykepengesoknadService(
    private val kafkaKeyGeneratorClient: KafkaKeyGeneratorClient,
    private val arbeidssokerregisterClient: ArbeidssokerregisterClient,
    private val arbeidssokerperiodeRepository: ArbeidssokerperiodeRepository,
    private val periodebekreftelseRepository: PeriodebekreftelseRepository,
    private val paaVegneAvProducer: ArbeidssokerperiodePaaVegneAvProducer,
    private val bekreftelseProducer: ArbeidssokerperiodeBekreftelseProducer,
) {
    private val log = logger()

    @Transactional
    fun behandleSoknad(soknad: SykepengesoknadDTO) {
        when {
            soknad.erFremtidigFriskTilArbeidSoknad() -> behandleVedtaksperiode(soknad)

            soknad.erSendtFriskTilArbeidSoknad() -> behandleBekreftelse(soknad)
        }
    }

    fun sendPaaVegneAvStartMelding(
        arbeidssokerperiode: Arbeidssokerperiode,
        graceMs: Long,
    ) = paaVegneAvProducer.send(
        PaaVegneAvStartMelding(
            kafkaKey = arbeidssokerperiode.kafkaRecordKey!!,
            arbeidssokerperiodeId = arbeidssokerperiode.id!!,
            arbeidssokerregisterPeriodeId = arbeidssokerperiode.arbeidssokerperiodeId!!,
            graceMS = graceMs,
        ),
    )

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

        sendPaaVegneAvStartMelding(
            lagretArbeidssokerperiode,
            beregnGraceMS(vedtaksperiode.tom, SOKNAD_DEAKTIVERES_ETTER_MAANEDER),
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
                "Ignorerer periodebekreftelse for søknad: ${soknad.id} med " +
                    "vedtaksperiodeId: ${soknad.friskTilArbeidVedtakId} siden ignorerArbeidssokerregisterer satt.",
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

        if (!erAvsluttendeSoknad && soknad.fortsattArbeidssoker == null) {
            throw PeriodebekreftelseException(
                "Mangler verdi for fortsattArbeidssoker i søknad: ${soknad.id} med " +
                    "vedtaksperiode: ${soknad.friskTilArbeidVedtakId} og " +
                    "arbeidssokerperiode: ${arbeidssokerperiode.id} som skal være satt da søknaden " +
                    "ikke er siste i perioden.",
            )
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

        // Vi sender PaaVegneAvStoppMelding hvis søknaden er siste i perioden eller bruker har svart at hen ikke vil
        // fortsette å være Friskmeldt til Arbeidsformidling. Vi frasier oss da ansvaret for innsending av
        // Periodebekreftelser, men melder ikke bruker ut av Arbeidssøkerregisteret.
        if (erAvsluttendeSoknad || soknad.fortsattArbeidssoker == false) {
            val avsluttetAarsak = if (erAvsluttendeSoknad) AvsluttetAarsak.AVSLUTTET_PERIODE else AvsluttetAarsak.BRUKER
            arbeidssokerperiode.lagreAvsluttetAarsak(avsluttetAarsak)
            sendPaaVegneAvStoppMelding(arbeidssokerperiode)
        } else {
            sendBekreftelseMelding(arbeidssokerperiode, soknad)
        }

        log.info(
            "Behandlet søknad: ${soknad.id} med " +
                "vedtaksperiode: ${soknad.friskTilArbeidVedtakId} for " +
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
                arbeidssokerperiodeId = arbeidssokerperiode.id!!,
                arbeidssokerregisterPeriodeId = arbeidssokerperiode.arbeidssokerperiodeId!!,
                fnr = sykepengesoknadDTO.fnr,
                periodeStart = sykepengesoknadDTO.fom!!.toInstantAtStartOfDayOsloTid(),
                periodeSlutt = sykepengesoknadDTO.tom!!.plusDays(1).toInstantAtStartOfDayOsloTid(),
                inntektUnderveis = sykepengesoknadDTO.inntektUnderveis,
                fortsattArbeidssoker = true,
            )
        bekreftelseProducer.send(bekreftelseMelding)
    }

    private fun sendPaaVegneAvStoppMelding(arbeidssokerperiode: Arbeidssokerperiode) =
        paaVegneAvProducer.send(
            PaaVegneAvStoppMelding(
                kafkaKey = arbeidssokerperiode.kafkaRecordKey!!,
                arbeidssokerperiodeId = arbeidssokerperiode.id!!,
                arbeidssokerregisterPeriodeId = arbeidssokerperiode.arbeidssokerperiodeId!!,
            ),
        )

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

fun LocalDate.toInstantAtStartOfDayOsloTid(): Instant = this.atStartOfDay(ZoneId.of("Europe/Oslo")).toInstant()

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
