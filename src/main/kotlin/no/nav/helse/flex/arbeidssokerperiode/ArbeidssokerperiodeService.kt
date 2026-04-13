package no.nav.helse.flex.arbeidssokerperiode

import no.nav.helse.flex.logger
import no.nav.helse.flex.sykepengesoknad.ArbeidssokerperiodeStoppProducer
import no.nav.helse.flex.sykepengesoknad.StoppMelding
import no.nav.paw.arbeidssokerregisteret.api.v1.Periode
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.concurrent.TimeUnit

@Service
class ArbeidssokerperiodeService(
    private val arbeidssokerperiodeRepository: ArbeidssokerperiodeRepository,
    private val arbeidssokerperiodeStoppProducer: ArbeidssokerperiodeStoppProducer,
) {
    private val log = logger()

    @Scheduled(initialDelay = 4, fixedDelay = 3600, timeUnit = TimeUnit.MINUTES)
    fun slettEnkeltArbeidssokerperiode() {
        arbeidssokerperiodeRepository.findById("cf4176af-535b-4fbe-a9d1-eeba32abba16").ifPresent {
            arbeidssokerperiodeRepository.deleteById("cf4176af-535b-4fbe-a9d1-eeba32abba16")
            log.info("Slettet arbeidssøkerperiode med id: cf4176af-535b-4fbe-a9d1-eeba32abba16")
        }
    }

    @Transactional
    fun behandlePeriode(periode: Periode) {
        // Behandler ikke perioder som ikke er avsluttet.
        if (periode.avsluttet == null) {
            return
        }

        arbeidssokerperiodeRepository
            .findByArbeidssokerperiodeId(periode.id.toString())
            .forEach { arbeidssokerperiode ->
                // Hopper over arbeidssøkerperioder hvor vi allerede har mottatt avsluttet periode fra arbeidsøkerregisteret.
                if (arbeidssokerperiode.avsluttetMottatt == null) {
                    prosesserPeriode(arbeidssokerperiode, periode)
                }
            }
    }

    private fun prosesserPeriode(
        arbeidssokerperiode: Arbeidssokerperiode,
        arbeidssokerregisterPeriode: Periode,
    ) {
        arbeidssokerperiodeRepository.save(
            arbeidssokerperiode.copy(
                avsluttetMottatt = Instant.now(),
                avsluttetTidspunkt = arbeidssokerregisterPeriode.avsluttet.tidspunkt,
            ),
        )

        arbeidssokerperiodeStoppProducer.send(
            StoppMelding(
                vedtaksperiodeId = arbeidssokerperiode.vedtaksperiodeId,
                fnr = arbeidssokerperiode.fnr,
                avsluttetTidspunkt = arbeidssokerregisterPeriode.avsluttet.tidspunkt,
            ),
        )
        log.info(
            "Avsluttet arbeidssøkerperiode: ${arbeidssokerperiode.id} for " +
                "vedtaksperiode: ${arbeidssokerperiode.vedtaksperiodeId} og " +
                "periode i arbeidssøkerregisteret: ${arbeidssokerperiode.arbeidssokerperiodeId}.",
        )
    }
}
