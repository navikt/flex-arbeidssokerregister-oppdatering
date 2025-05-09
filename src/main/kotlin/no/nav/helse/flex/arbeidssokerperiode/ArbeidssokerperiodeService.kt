package no.nav.helse.flex.arbeidssokerperiode

import no.nav.helse.flex.EnvironmentToggles
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
    private val environmentToggles: EnvironmentToggles,
) {
    private val log = logger()

    @Scheduled(initialDelay = 3, fixedDelay = 3600, timeUnit = TimeUnit.MINUTES)
    fun oppdaterArbeidssokerperiodeId() {
        if (!environmentToggles.erProduksjon()) {
            return
        }

        val gammelArbeidssokerperiodeId = "0600a081-775e-43b0-99ac-9ea7a2f0ee61"
        val nyArbeidssokerperiodeId = "b809720e-564a-4c84-9938-4df3095bb15e"

        val arbeidssokerperiode = arbeidssokerperiodeRepository.findByArbeidssokerperiodeId(gammelArbeidssokerperiodeId)

        assert(arbeidssokerperiode!!.id == "3caa2c37-c3ee-4fb8-adea-b8f6cf20a96b")
        assert(arbeidssokerperiode.vedtaksperiodeId == "38173d76-1ef2-4e62-8b61-31b314b9ad3d")

        arbeidssokerperiodeRepository.save(
            arbeidssokerperiode.copy(arbeidssokerperiodeId = nyArbeidssokerperiodeId),
        )

        log.info(
            "Oppdatert arbeidssøkerperiode med id ${arbeidssokerperiode.id} " +
                "og vedtaksperiodeId: ${arbeidssokerperiode.vedtaksperiodeId} " +
                "fra: $gammelArbeidssokerperiodeId til: $nyArbeidssokerperiodeId",
        )
    }

    @Transactional
    fun behandlePeriode(periode: Periode) {
        // Behandler ikke perioder som ikke er avsluttet.
        if (periode.avsluttet == null) {
            return
        }

        val arbeidssokerperiode =
            arbeidssokerperiodeRepository.findByArbeidssokerperiodeId(periode.id.toString())

        // Behandler ikke ukjente periode i arbeidssøkerregisteret.
        if (arbeidssokerperiode == null) {
            return
        }

        // Behandler ikke en allerede avsluttet arbeidssøkerperiode.
        if (arbeidssokerperiode.avsluttetMottatt != null) {
            return
        }

        prosesserPeriode(arbeidssokerperiode, periode)
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
