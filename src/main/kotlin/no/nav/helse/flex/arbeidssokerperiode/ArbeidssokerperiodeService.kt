package no.nav.helse.flex.arbeidssokerperiode

import no.nav.helse.flex.logger
import no.nav.helse.flex.sykepengesoknad.ArbeidssokerperiodeStoppProducer
import no.nav.helse.flex.sykepengesoknad.StoppMelding
import no.nav.paw.arbeidssokerregisteret.api.v1.Periode
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
class ArbeidssokerperiodeService(
    private val arbeidssokerperiodeRepository: ArbeidssokerperiodeRepository,
    private val arbeidssokerperiodeStoppProducer: ArbeidssokerperiodeStoppProducer,
) {
    private val log = logger()

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
