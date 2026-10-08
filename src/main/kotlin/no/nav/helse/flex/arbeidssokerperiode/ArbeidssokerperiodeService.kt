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
        val arbeidssokerperiode = finnRestartbarArbeidssokerperiode(arbeidssokerregisterPeriode) ?: return

        if (arbeidssokerperiode.tomErEldreEnn(PERIODE_TOM_MAX_ALDER_MAANEDER)) {
            log.info(
                "Restarter ikke avsluttet arbeidssøkerperiode: ${arbeidssokerperiode.id} for " +
                    "vedtaksperiode: ${arbeidssokerperiode.vedtaksperiodeId} med " +
                    "vedtaksperiode.tom: ${arbeidssokerperiode.vedtaksperiodeTom} siden den er eldre enn " +
                    "$PERIODE_TOM_MAX_ALDER_MAANEDER måneder. Ny periode i arbeidssøkerregisteret: ${arbeidssokerregisterPeriode.id}.",
            )
            return
        }

        restartArbeidssokerperiode(arbeidssokerperiode, arbeidssokerregisterPeriode)
    }

    private fun finnRestartbarArbeidssokerperiode(arbeidssokerregisterPeriode: Periode): Arbeidssokerperiode? =
        arbeidssokerperiodeRepository
            .findByFnr(arbeidssokerregisterPeriode.identitetsnummer)
            .maxByOrNull { it.vedtaksperiodeTom }
            ?.takeIf { it.kanRestartesMed(arbeidssokerregisterPeriode) }

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

        sendStartStoppMelding(StartStop.START, restartetArbeidssokerperiode, arbeidssokerregisterPeriode.startet.tidspunkt)

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

    private fun Arbeidssokerperiode.kanRestartesMed(arbeidssokerregisterPeriode: Periode): Boolean =
        erAvsluttetAvArbeidssokerregisteret() && arbeidssokerperiodeId != arbeidssokerregisterPeriode.id.toString()

    private fun Arbeidssokerperiode.erAvsluttetAvArbeidssokerregisteret() = avsluttetMottatt != null && sendtAvsluttet == null

    private fun Arbeidssokerperiode.tomErEldreEnn(maaneder: Int) =
        vedtaksperiodeTom.isBefore(LocalDate.now().minusMonths(maaneder.toLong()))
}
