package no.nav.helse.flex.arbeidssokerperiode

import no.nav.helse.flex.EnvironmentToggles
import no.nav.helse.flex.logger
import no.nav.helse.flex.sykepengesoknad.ArbeidssokerperiodeStoppProducer
import no.nav.helse.flex.sykepengesoknad.StoppMelding
import no.nav.helse.flex.sykepengesoknad.VedtaksperiodeExceptionRepository
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
    private val vedtaksperiodeExceptionRepository: VedtaksperiodeExceptionRepository,
    private val environmentToggles: EnvironmentToggles,
) {
    private val log = logger()

    @Scheduled(initialDelay = 2, fixedDelay = 1, timeUnit = TimeUnit.MINUTES)
    fun sendStoppMelding() {
        if (environmentToggles.erProduksjon()) {
            listOf(
                "bedb05d3-a2ff-4ee3-8525-44965b21442c",
                "5abde058-fc10-470f-a336-0daccd7ed733",
            ).forEach {
                vedtaksperiodeExceptionRepository.findByVedtaksperiodeId(it).first().also {
//                    arbeidssokerperiodeStoppProducer.send(
//                        StoppMelding(
//                            vedtaksperiodeId = it.vedtaksperiodeId,
//                            fnr = it.fnr,
//                            avsluttetTidspunkt = Instant.now(),
//                        ),
//                    )
//                    vedtaksperiodeExceptionRepository.deleteByFnrAndVedtaksperiodeId(
//                        fnr = it.fnr,
//                        vedtaksperiodeId = it.vedtaksperiodeId,
//                    )
                    log.info(
                        "Skulle sendt StoppMelding for vedtaksperiode: ${it.vedtaksperiodeId} og slettet fra vedtaksperiode_exception.",
                    )
                }
            }
        }
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
