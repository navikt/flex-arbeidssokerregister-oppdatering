package no.nav.helse.flex.sykepengesoknad

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.helse.flex.Arbeidssokerperiode
import no.nav.helse.flex.ArbeidssokerperiodeRepository
import no.nav.helse.flex.arbeidssokerregister.ArbeidssokerperiodePaaVegneAvProducer
import no.nav.helse.flex.arbeidssokerregister.ArbeidssokerperiodeRequest
import no.nav.helse.flex.arbeidssokerregister.ArbeidssokerperiodeResponse
import no.nav.helse.flex.arbeidssokerregister.ArbeidssokerregisterClient
import no.nav.helse.flex.arbeidssokerregister.KafkaKeyGeneratorClient
import no.nav.helse.flex.arbeidssokerregister.KafkaKeyGeneratorRequest
import no.nav.helse.flex.arbeidssokerregister.PaaVegneAvMelding
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
import java.util.*

const val SOKNAR_DEAKTIVERES_ETTER_MAANEDER = 4

@Service
class SykepengesoknadService(
    private val kafkaKeyGeneratorClient: KafkaKeyGeneratorClient,
    private val arbeidssokerregisterClient: ArbeidssokerregisterClient,
    private val arbeidssokerperiodeRepository: ArbeidssokerperiodeRepository,
    private val paaVegneAvProducer: ArbeidssokerperiodePaaVegneAvProducer,
) {
    private val log = logger()

    @Transactional
    fun behandleSoknad(sykepengesoknadDTO: SykepengesoknadDTO) {
        if (sykepengesoknadDTO.skalBehandles()) {
            behandleVedtaksperiode(sykepengesoknadDTO.tilVedtaksperiode())
        }
    }

    private fun behandleVedtaksperiode(vedtaksperiode: FriskTilArbeidVedtaksperiode) {
        if (!erNyVedtaksperiode(vedtaksperiode)) {
            return
        }

        val kafkaRecordKey = hentKafkaRecordKey(vedtaksperiode.fnr)
        val arbeidsokerperiode = hentArbeidssokerperiodeId(vedtaksperiode.fnr)

        if (arbeidsokerperiode.avsluttet != null) {
            val avsluttetTidspunkt = arbeidsokerperiode.avsluttet.tidspunkt.toLocalDate()
            throw ArbeidssokerperiodeException(
                "Arbeidssøkerperiode med id: ${arbeidsokerperiode.periodeId} ble avsluttet $avsluttetTidspunkt.",
            )
        }

        paaVegneAvProducer.send(
            PaaVegneAvMelding(
                kafkaRecordKey,
                UUID.fromString(arbeidsokerperiode.periodeId),
                beregnGraceMS(vedtaksperiode.tom, SOKNAR_DEAKTIVERES_ETTER_MAANEDER),
            ),
        )

        arbeidssokerperiodeRepository.save(
            vedtaksperiode.toArbeidssokerperiode(
                kafkaRecordKey,
                arbeidsokerperiode.periodeId,
                Instant.now(),
            ),
        )

        log.info(
            "Lagret vedtaksperiode med id: ${vedtaksperiode.vedtaksperiodeId} for arbeidssokerperiode: ${arbeidsokerperiode.periodeId}",
        )
    }

    private fun erNyVedtaksperiode(vedtaksperiode: FriskTilArbeidVedtaksperiode) =
        arbeidssokerperiodeRepository.findByVedtaksperiodeId(vedtaksperiode.vedtaksperiodeId) == null

    private fun SykepengesoknadDTO.skalBehandles() =
        type == SoknadstypeDTO.FRISKMELDT_TIL_ARBEIDSFORMIDLING && status == SoknadsstatusDTO.FREMTIDIG

    private fun hentKafkaRecordKey(fnr: String): Long = kafkaKeyGeneratorClient.hentKafkaKey(KafkaKeyGeneratorRequest(fnr))!!.key

    private fun hentArbeidssokerperiodeId(fnr: String): ArbeidssokerperiodeResponse =
        arbeidssokerregisterClient.hentSisteArbeidssokerperiode(ArbeidssokerperiodeRequest(fnr)).single()

    fun FriskTilArbeidVedtaksperiode.toArbeidssokerperiode(
        kafkaRecordKey: Long,
        arbeidssokerperiodeId: String,
        sendtPaaVegneAv: Instant,
    ) = Arbeidssokerperiode(
        fnr = this.fnr,
        vedtaksperiodeId = this.vedtaksperiodeId,
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

fun String.tilPeriode(): Periode = objectMapper.readValue(this)

fun Instant.toLocalDate() = this.atZone(ZoneId.systemDefault()).toLocalDate()

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

// Beregner antall millisekunder mellom dagen etter `tom` og `months` måneder senere.
fun beregnGraceMS(
    tom: LocalDate,
    months: Int,
): Long {
    val starttidspunkt = tom.plusDays(1).atStartOfDay()
    val sluttidspunkt = starttidspunkt.plusMonths(months.toLong())
    return Duration.between(starttidspunkt, sluttidspunkt).toMillis()
}
