package no.nav.helse.flex.sykepengesoknad

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
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import tools.jackson.module.kotlin.readValue
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
    private val environmentToggles: EnvironmentToggles,
) {
    private val log = logger()

    @Transactional
    fun behandleSoknad(soknad: SykepengesoknadDTO) {
        when {
            soknad.erFremtidigFriskTilArbeidSoknad() -> behandleVedtaksperiode(soknad)
            soknad.erSendtFriskTilArbeidSoknad() -> behandleBekreftelse(soknad)
            soknad.erUtgaattFriskTilArbeidSoknad() -> behandleUtgattSoknad(soknad)
        }
    }

    fun sendPaaVegneAvStartMelding(
        arbeidssokerperiode: Arbeidssokerperiode,
        graceMs: Long,
    ) = paaVegneAvProducer.send(arbeidssokerperiode.tilPaaVegneAvStartMelding(graceMs))

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

        val kafkaRecordKey = hentKafkaRecordKey(vedtaksperiode)

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

        val vedtaksperiodeId =
            requireNotNull(soknad.friskTilArbeidVedtakId) {
                "Mangler vedtaksperiodeId for søknad: ${soknad.id}."
            }

        val arbeidssokerperiode =
            arbeidssokerperiodeRepository.findByVedtaksperiodeId(vedtaksperiodeId)
                ?: throw PeriodebekreftelseException(
                    "Fant ikke arbeidssøkerperiode for søknad: ${soknad.id} med " +
                        "vedtaksperiode: ${soknad.friskTilArbeidVedtakId}.",
                )

        if (periodebekreftelseRepository.findBySykepengesoknadId(soknad.id) != null) {
            return
        }

        val erAvsluttendeSoknad = arbeidssokerperiode.vedtaksperiodeTom == soknad.tom

        if (!erAvsluttendeSoknad && soknad.fortsattArbeidssoker == null) {
            log.warn(
                "Mangler verdi for fortsattArbeidssoker i søknad: ${soknad.id} med soknad.tom: ${soknad.tom}, " +
                    "vedtaksperiode: ${soknad.friskTilArbeidVedtakId} og arbeidssokerperiode: ${arbeidssokerperiode.id} " +
                    "med vedtaksperiode.tom: ${arbeidssokerperiode.vedtaksperiodeTom}. Verdien skal kun mangle på " +
                    "siste søknad i perioden, men dette kan endre seg ved korrigeriner av perioden.",
            )
        }

        periodebekreftelseRepository.save(
            Periodebekreftelse(
                arbeidssokerperiodeId =
                    checkNotNull(arbeidssokerperiode.id) {
                        "Mangler arbeidssøkerperiodeId for søknad: ${soknad.id} med vedtaksperiode: $vedtaksperiodeId."
                    },
                sykepengesoknadId = soknad.id,
                fortsattArbeidssoker = soknad.fortsattArbeidssoker,
                inntektUnderveis = soknad.inntektUnderveis,
                opprettet = Instant.now(),
                avsluttendeSoknad = erAvsluttendeSoknad,
            ),
        )

        // Sender PaaVegneAvStoppMelding hvis søknaden er siste i perioden eller bruker har svart at hen ikke vil
        // fortsette å være Friskmeldt til Arbeidsformidling. Applikasjonen sier da fra fra seg ansvaret for
        // innsending av Periodebekreftelser, men melder ikke bruker ut av Arbeidssøkerregisteret.
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

    private fun behandleUtgattSoknad(soknad: SykepengesoknadDTO) {
        arbeidssokerperiodeRepository
            .findByFnr(soknad.fnr)
            .filter { it.sendtAvsluttet == null && it.avsluttetAarsak != AvsluttetAarsak.UTGAATT }
            .forEach {
                it.lagreAvsluttetAarsak(AvsluttetAarsak.UTGAATT)
                sendPaaVegneAvStoppMelding(it)
                log.info("Sendte PaaVegneAvStoppMelding for arbeidssøkerperiode: ${it.id} grunnet UTGATT søknad: ${soknad.id}.")
            }
    }

    private fun sendBekreftelseMelding(
        arbeidssokerperiode: Arbeidssokerperiode,
        sykepengesoknadDTO: SykepengesoknadDTO,
    ) {
        val bekreftelseMelding =
            BekreftelseMelding(
                kafkaKey =
                    checkNotNull(arbeidssokerperiode.kafkaRecordKey) {
                        "Mangler Kafka-key for søknad: ${sykepengesoknadDTO.id} med arbeidssøkerperiode: ${arbeidssokerperiode.id}."
                    },
                arbeidssokerperiodeId =
                    checkNotNull(arbeidssokerperiode.id) {
                        "Mangler arbeidssøkerperiodeId for søknad: ${sykepengesoknadDTO.id} med " +
                            "vedtaksperiode: ${arbeidssokerperiode.vedtaksperiodeId}."
                    },
                arbeidssokerregisterPeriodeId =
                    checkNotNull(arbeidssokerperiode.arbeidssokerperiodeId) {
                        "Mangler periodeId i arbeidssøkerregisteret for søknad: ${sykepengesoknadDTO.id} med " +
                            "arbeidssøkerperiode: ${arbeidssokerperiode.id}."
                    },
                fnr = sykepengesoknadDTO.fnr,
                periodeStart =
                    requireNotNull(sykepengesoknadDTO.fom) {
                        "Mangler fom for søknad: ${sykepengesoknadDTO.id}."
                    }.toInstantAtStartOfDayOsloTid(),
                periodeSlutt =
                    requireNotNull(sykepengesoknadDTO.tom) {
                        "Mangler tom for søknad: ${sykepengesoknadDTO.id}."
                    }.plusDays(1).toInstantAtStartOfDayOsloTid(),
                inntektUnderveis = sykepengesoknadDTO.inntektUnderveis,
                fortsattArbeidssoker = true,
            )
        bekreftelseProducer.send(bekreftelseMelding)
    }

    private fun sendPaaVegneAvStoppMelding(arbeidssokerperiode: Arbeidssokerperiode) =
        paaVegneAvProducer.send(
            PaaVegneAvStoppMelding(
                kafkaKey =
                    checkNotNull(arbeidssokerperiode.kafkaRecordKey) {
                        "Mangler Kafka-key for arbeidssøkerperiode: ${arbeidssokerperiode.id} med " +
                            "vedtaksperiode: ${arbeidssokerperiode.vedtaksperiodeId}."
                    },
                arbeidssokerperiodeId =
                    checkNotNull(arbeidssokerperiode.id) {
                        "Mangler arbeidssøkerperiodeId for vedtaksperiode: ${arbeidssokerperiode.vedtaksperiodeId}."
                    },
                arbeidssokerregisterPeriodeId =
                    checkNotNull(arbeidssokerperiode.arbeidssokerperiodeId) {
                        "Mangler periodeId i arbeidssøkerregisteret for arbeidssøkerperiode: ${arbeidssokerperiode.id} med " +
                            "vedtaksperiode: ${arbeidssokerperiode.vedtaksperiodeId}."
                    },
            ),
        )

    private fun erNyVedtaksperiode(vedtaksperiode: FriskTilArbeidVedtaksperiode) =
        arbeidssokerperiodeRepository.findByVedtaksperiodeId(vedtaksperiode.vedtaksperiodeId) == null

    private fun hentKafkaRecordKey(vedtaksperiode: FriskTilArbeidVedtaksperiode): Long =
        kafkaKeyGeneratorClient.hentKafkaKey(KafkaKeyGeneratorRequest(vedtaksperiode.fnr))?.key
            ?: throw ArbeidssokerperiodeException(
                "Mangler Kafka-key for søknad: ${vedtaksperiode.sykepengesoknadId} med " +
                    "vedtaksperiode: ${vedtaksperiode.vedtaksperiodeId}.",
            )

    private fun hentArbeidssokerperiodeId(fnr: String): List<ArbeidssokerperiodeResponse> =
        arbeidssokerregisterClient.hentSisteArbeidssokerperiode(ArbeidssokerperiodeRequest(fnr))

    private fun SykepengesoknadDTO.erFremtidigFriskTilArbeidSoknad() =
        type == SoknadstypeDTO.FRISKMELDT_TIL_ARBEIDSFORMIDLING && status == SoknadsstatusDTO.FREMTIDIG

    private fun SykepengesoknadDTO.erSendtFriskTilArbeidSoknad() =
        type == SoknadstypeDTO.FRISKMELDT_TIL_ARBEIDSFORMIDLING && status == SoknadsstatusDTO.SENDT

    private fun SykepengesoknadDTO.erUtgaattFriskTilArbeidSoknad() =
        type == SoknadstypeDTO.FRISKMELDT_TIL_ARBEIDSFORMIDLING && status == SoknadsstatusDTO.UTGAATT

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
        val periode =
            requireNotNull(friskTilArbeidVedtakPeriode) {
                "Mangler friskTilArbeidVedtakPeriode for søknad: $id med vedtaksperiode: $friskTilArbeidVedtakId."
            }.tilPeriode()
        return FriskTilArbeidVedtaksperiode(
            fnr = this.fnr,
            vedtaksperiodeId =
                requireNotNull(friskTilArbeidVedtakId) {
                    "Mangler vedtaksperiodeId for søknad: $id."
                },
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
