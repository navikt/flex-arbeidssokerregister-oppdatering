package no.nav.helse.flex.arbeidssokerperiode

import no.nav.helse.flex.Arbeidssokerperiode
import no.nav.helse.flex.ArbeidssokerperiodeRepository
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
    fun behandlePeriode(arbeidssokerregisterPeriode: Periode) {
        if (arbeidssokerregisterPeriode.avsluttet == null) {
            log.info("Behandler ikke uavsluttet arbeidssokerperiode med id: ${arbeidssokerregisterPeriode.id}.")
            return
        }

        val arbeidssokerperiode =
            arbeidssokerperiodeRepository.findByArbeidssokerperiodeId(arbeidssokerregisterPeriode.id.toString())

        if (arbeidssokerperiode == null) {
            log.info("Behandler ikke ukjent avsluttet arbeidssokerperiode med id: ${arbeidssokerregisterPeriode.id}.")
            return
        }

        if (arbeidssokerperiode.avsluttetMottatt != null) {
            log.info("Behandler ikke allerede avsluttet arbeidssokerperiode med id: ${arbeidssokerregisterPeriode.id}.")
            return
        }

        prosesserPeriode(arbeidssokerperiode, arbeidssokerregisterPeriode)
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
            "Behandlet avsluttet arbeidssokerperiode med id: ${arbeidssokerperiode.id} for " +
                "vedtaksperiodeId: ${arbeidssokerperiode.vedtaksperiodeId}.",
        )
    }
}
