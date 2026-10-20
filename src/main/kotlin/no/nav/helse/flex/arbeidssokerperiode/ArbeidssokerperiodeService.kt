package no.nav.helse.flex.arbeidssokerperiode

import no.nav.helse.flex.arbeidssokerregister.ArbeidssokerperiodePaaVegneAvProducer
import no.nav.helse.flex.arbeidssokerregister.tilPaaVegneAvStartMelding
import no.nav.helse.flex.logger
import no.nav.helse.flex.sykepengesoknad.ArbeidssokerperiodeStartStoppProducer
import no.nav.helse.flex.sykepengesoknad.SOKNAD_DEAKTIVERES_ETTER_MAANEDER
import no.nav.helse.flex.sykepengesoknad.StartStop
import no.nav.helse.flex.sykepengesoknad.StartStoppMelding
import no.nav.helse.flex.sykepengesoknad.beregnGraceMS
import no.nav.paw.arbeidssokerregisteret.api.v1.Periode
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDate

const val PERIODE_TOM_MAX_ALDER_MAANEDER = 4

@Service
class ArbeidssokerperiodeService(
    private val arbeidssokerperiodeRepository: ArbeidssokerperiodeRepository,
    private val arbeidssokerperiodeStartStoppProducer: ArbeidssokerperiodeStartStoppProducer,
    private val paaVegneAvProducer: ArbeidssokerperiodePaaVegneAvProducer,
) {
    private val log = logger()

    @Transactional
    fun behandlePeriode(periode: Periode) {
        if (periode.avsluttet == null) {
            behandleStartetPeriode(periode)
        } else {
            behandleAvsluttetPeriode(periode)
        }
    }

    private fun behandleStartetPeriode(arbeidssokerregisterPeriode: Periode) {
        finnRestartbarArbeidssokerperiode(arbeidssokerregisterPeriode)?.let {
            restartArbeidssokerperiode(it, arbeidssokerregisterPeriode)
        }
    }

    // Restarter kun siste arbeidssøkerperiode for en gitt bruker hvis vi har mottatt avsluttet periode fra
    // arbeidssøkerregisteret og ikke har sendt PaaVegneAv, samtidig som den ikke allerede er restartet og
    // vedtaksperiode.tom ikke er for gammel.
    private fun finnRestartbarArbeidssokerperiode(arbeidssokerregisterPeriode: Periode): Arbeidssokerperiode? {
        val sisteArbeidssokerperiode =
            arbeidssokerperiodeRepository
                .findByFnr(arbeidssokerregisterPeriode.identitetsnummer)
                .maxByOrNull { it.vedtaksperiodeTom } ?: return null

        return when (val aarsak = sisteArbeidssokerperiode.aarsakTilAtIkkeRestartbar(arbeidssokerregisterPeriode)) {
            null -> sisteArbeidssokerperiode
            else -> {
                log.info(
                    "Mottok ny periode i arbeidssøkerregisteret: ${arbeidssokerregisterPeriode.id}, men  " +
                        "restarter ikke arbeidssøkerperiode: ${sisteArbeidssokerperiode.id} for " +
                        "vedtaksperiode: ${sisteArbeidssokerperiode.vedtaksperiodeId} med " +
                        "vedtaksperiode.tom: ${sisteArbeidssokerperiode.vedtaksperiodeTom} og eksisterende periode i " +
                        "arbeidssøkerregisteret: ${sisteArbeidssokerperiode.arbeidssokerperiodeId}. " +
                        "Årsak: $aarsak - ${aarsak.beskrivelse}",
                )
                null
            }
        }
    }

    private fun restartArbeidssokerperiode(
        arbeidssokerperiode: Arbeidssokerperiode,
        arbeidssokerregisterPeriode: Periode,
    ) {
        val restartetArbeidssokerperiode =
            arbeidssokerperiodeRepository.save(
                arbeidssokerperiode.copy(
                    arbeidssokerperiodeId = arbeidssokerregisterPeriode.id.toString(),
                    sendtPaaVegneAv = Instant.now(),
                    avsluttetMottatt = null,
                    avsluttetTidspunkt = null,
                ),
            )

        paaVegneAvProducer.send(
            restartetArbeidssokerperiode.tilPaaVegneAvStartMelding(
                beregnGraceMS(restartetArbeidssokerperiode.vedtaksperiodeTom, SOKNAD_DEAKTIVERES_ETTER_MAANEDER),
            ),
        )

        sendStartStoppMelding(
            StartStop.START,
            restartetArbeidssokerperiode,
            arbeidssokerregisterPeriode.startet.tidspunkt,
        )

        log.info(
            "Restartet arbeidssøkerperiode: ${restartetArbeidssokerperiode.id} for " +
                "vedtaksperiode: ${restartetArbeidssokerperiode.vedtaksperiodeId}. Byttet periode i " +
                "arbeidssøkerregisteret fra: ${arbeidssokerperiode.arbeidssokerperiodeId} " +
                "til: ${restartetArbeidssokerperiode.arbeidssokerperiodeId}.",
        )
    }

    private fun behandleAvsluttetPeriode(arbeidssokerregisterPeriode: Periode) {
        arbeidssokerperiodeRepository
            .findByArbeidssokerperiodeId(arbeidssokerregisterPeriode.id.toString())
            .filter { it.avsluttetMottatt == null }
            .forEach { avsluttArbeidssokerperiode(it, arbeidssokerregisterPeriode) }
    }

    private fun avsluttArbeidssokerperiode(
        arbeidssokerperiode: Arbeidssokerperiode,
        arbeidssokerregisterPeriode: Periode,
    ) {
        val avsluttetTidspunkt = arbeidssokerregisterPeriode.avsluttet.tidspunkt

        arbeidssokerperiodeRepository.save(
            arbeidssokerperiode.copy(
                avsluttetMottatt = Instant.now(),
                avsluttetTidspunkt = avsluttetTidspunkt,
            ),
        )

        sendStartStoppMelding(StartStop.STOPP, arbeidssokerperiode, avsluttetTidspunkt)

        log.info(
            "Avsluttet arbeidssøkerperiode: ${arbeidssokerperiode.id} for " +
                "vedtaksperiode: ${arbeidssokerperiode.vedtaksperiodeId} og " +
                "periode i arbeidssøkerregisteret: ${arbeidssokerperiode.arbeidssokerperiodeId}.",
        )
    }

    private fun sendStartStoppMelding(
        operation: StartStop,
        arbeidssokerperiode: Arbeidssokerperiode,
        tidspunkt: Instant,
    ) = arbeidssokerperiodeStartStoppProducer.send(
        StartStoppMelding(
            operation = operation,
            vedtaksperiodeId = arbeidssokerperiode.vedtaksperiodeId,
            fnr = arbeidssokerperiode.fnr,
            tidspunkt = tidspunkt,
        ),
    )

    private fun Arbeidssokerperiode.aarsakTilAtIkkeRestartbar(arbeidssokerregisterPeriode: Periode): IkkeRestartbarAarsak? =
        when {
            harIkkeMottattAvsluttetFraArbeidssokerregisteret() -> IkkeRestartbarAarsak.IKKE_MOTTATT_AVSLUTTET
            harSendtPaaVegneAvAvsluttet() -> IkkeRestartbarAarsak.SENDT_PAA_VEGNE_AV_STOPP
            erAlleredeRestartet(arbeidssokerregisterPeriode) -> IkkeRestartbarAarsak.ALLEREDE_RESTARTET
            periodensTomErEldreEnn(PERIODE_TOM_MAX_ALDER_MAANEDER) -> IkkeRestartbarAarsak.VEDTAKSPERIODE_FOR_GAMMEL
            else -> null
        }

    private fun Arbeidssokerperiode.harIkkeMottattAvsluttetFraArbeidssokerregisteret() = avsluttetMottatt == null

    private fun Arbeidssokerperiode.harSendtPaaVegneAvAvsluttet() = sendtAvsluttet != null

    private fun Arbeidssokerperiode.erAlleredeRestartet(arbeidssokerregisterPeriode: Periode) =
        arbeidssokerperiodeId == arbeidssokerregisterPeriode.id.toString()

    private fun Arbeidssokerperiode.periodensTomErEldreEnn(maaneder: Int) =
        vedtaksperiodeTom.isBefore(LocalDate.now().minusMonths(maaneder.toLong()))

    private enum class IkkeRestartbarAarsak(
        val beskrivelse: String,
    ) {
        IKKE_MOTTATT_AVSLUTTET("Har ikke mottatt avsluttet periode fra arbeidssøkerregisteret."),
        SENDT_PAA_VEGNE_AV_STOPP("Har sendt PaaVegneAvStopp-melding for for perioden."),
        ALLEREDE_RESTARTET("Perioden er allerede restartet av ny periode i arbeidssøkerregisteret."),
        VEDTAKSPERIODE_FOR_GAMMEL("Vedtaksperiode.tom er eldre enn $PERIODE_TOM_MAX_ALDER_MAANEDER måneder."),
    }
}
