package no.nav.helse.flex.sykepengesoknad

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.helse.flex.arbeidssokerperiode.Arbeidssokerperiode
import no.nav.helse.flex.arbeidssokerperiode.ArbeidssokerperiodeRepository
import no.nav.helse.flex.arbeidssokerregister.ArbeidssokerperiodeBekreftelseProducer
import no.nav.helse.flex.arbeidssokerregister.ArbeidssokerperiodePaaVegneAvProducer
import no.nav.helse.flex.arbeidssokerregister.ArbeidssokerperiodeRequest
import no.nav.helse.flex.arbeidssokerregister.ArbeidssokerperiodeResponse
import no.nav.helse.flex.arbeidssokerregister.ArbeidssokerregisterClient
import no.nav.helse.flex.arbeidssokerregister.BekreftelseMelding
import no.nav.helse.flex.arbeidssokerregister.KafkaKeyGeneratorClient
import no.nav.helse.flex.arbeidssokerregister.KafkaKeyGeneratorRequest
import no.nav.helse.flex.arbeidssokerregister.PaaVegneAvStartMelding
import no.nav.helse.flex.arbeidssokerregister.PaaVegneAvStoppMelding
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
) {
    private val log = logger()

    @Transactional
    fun behandleSoknad(sykepengesoknadDTO: SykepengesoknadDTO) {
        when {
            sykepengesoknadDTO.erFremtidigFriskTilArbeidSoknad() -> behandleVedtaksperiode(sykepengesoknadDTO.tilVedtaksperiode())
            sykepengesoknadDTO.erSendtFriskTilArbeidSoknad() -> behandleBekreftelse(sykepengesoknadDTO)
            else -> {
                if (sykepengesoknadDTO.erFriskTilArbeidSoknad()) {
                    log.info("Behandler ikke søknadstype: ${sykepengesoknadDTO.type} med status: ${sykepengesoknadDTO.status}.")
                }
            }
        }
    }

    private fun behandleVedtaksperiode(vedtaksperiode: FriskTilArbeidVedtaksperiode) {
        if (!erNyVedtaksperiode(vedtaksperiode)) {
            return
        }

        val kafkaRecordKey = hentKafkaRecordKey(vedtaksperiode.fnr)
        val arbeidsokerperiodeResponse = hentArbeidssokerperiodeId(vedtaksperiode.fnr)

        if (arbeidsokerperiodeResponse.avsluttet != null) {
            val avsluttetTidspunkt = arbeidsokerperiodeResponse.avsluttet.tidspunkt.toLocalDate()
            throw ArbeidssokerperiodeException(
                "Arbeidssøkerregisterperiode: ${arbeidsokerperiodeResponse.periodeId} ble avsluttet $avsluttetTidspunkt.",
            )
        }

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
            "Behandlet vedtaksperiode: ${vedtaksperiode.vedtaksperiodeId} for arbeidssøkerregisterperiode: ${arbeidsokerperiodeResponse.periodeId}.",
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
                "Fant ingen arbeidssøkerperiode for vedtaksperiode: ${sykepengesoknadDTO.friskTilArbeidVedtakId}.",
            )
        }

        val erAvsluttendeSoknad = arbeidssokerperiode.vedtaksperiodeTom == sykepengesoknadDTO.tom

        if (!erAvsluttendeSoknad) {
            if (sykepengesoknadDTO.fortsattArbeidssoker == null || sykepengesoknadDTO.inntektUnderveis == null) {
                throw lagPeriodebekreftelseException(sykepengesoknadDTO, sykepengesoknadDTO.friskTilArbeidVedtakId)
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
            arbeidssokerperiodeRepository.save(arbeidssokerperiode.copy(sendtAvsluttet = Instant.now()))
            sendPaaVegneAvStoppMelding(arbeidssokerperiode)
        } else {
            sendBekreftelseMelding(arbeidssokerperiode, sykepengesoknadDTO)
        }

        log.info("Behandlet periodebekreftelse for vedtaksperiode: ${sykepengesoknadDTO.friskTilArbeidVedtakId}.")
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

    private fun hentArbeidssokerperiodeId(fnr: String): ArbeidssokerperiodeResponse =
        arbeidssokerregisterClient.hentSisteArbeidssokerperiode(ArbeidssokerperiodeRequest(fnr)).single()

    private fun SykepengesoknadDTO.erFriskTilArbeidSoknad() = type == SoknadstypeDTO.FRISKMELDT_TIL_ARBEIDSFORMIDLING

    private fun SykepengesoknadDTO.erFremtidigFriskTilArbeidSoknad() =
        type == SoknadstypeDTO.FRISKMELDT_TIL_ARBEIDSFORMIDLING && status == SoknadsstatusDTO.FREMTIDIG

    private fun SykepengesoknadDTO.erSendtFriskTilArbeidSoknad() =
        type == SoknadstypeDTO.FRISKMELDT_TIL_ARBEIDSFORMIDLING && status == SoknadsstatusDTO.SENDT

    private fun lagPeriodebekreftelseException(
        soknad: SykepengesoknadDTO,
        friskTilArbeidVedtakId: String?,
    ): PeriodebekreftelseException {
        val manglerVerdi = if (soknad.fortsattArbeidssoker == null) "fortsattArbeidssoker" else "inntektUnderveis"
        return PeriodebekreftelseException(
            "Mangler $manglerVerdi for vedtaksperiode: $friskTilArbeidVedtakId og sykepengesøknad: ${soknad.id} " +
                "som skal være satt da søknaden ikke er siste søknad i perioden.",
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
