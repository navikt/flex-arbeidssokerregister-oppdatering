package no.nav.helse.flex.sykepengesoknad

import no.nav.helse.flex.arbeidssokerperiode.ArbeidssokerperiodeRepository
import no.nav.helse.flex.logger
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.util.concurrent.TimeUnit

@Service
class PatchPeriodeService(
    private val arbeidssokerperiodeRepository: ArbeidssokerperiodeRepository,
) {
    private val log = logger()

    @Scheduled(initialDelay = 3, fixedDelay = 3600, timeUnit = TimeUnit.MINUTES)
    fun patchTomDato() {
        listOf(
            PatchPeriode("8b3d31eb-22e6-4541-a916-8e0c07251e1f", LocalDate.of(2025, 5, 28)),
            PatchPeriode("1a5cf7ab-9b6a-431f-82da-e2288998029a", LocalDate.of(2025, 4, 25)),
            PatchPeriode("8a8ad333-15a7-4860-a635-c3db950ff398", LocalDate.of(2025, 6, 23)),
        ).forEach { periode ->

            val opprinneligPeriode = arbeidssokerperiodeRepository.findByVedtaksperiodeId(periode.vedtaksperiodeId)!!
            val oppdatertPeriode = opprinneligPeriode.copy(vedtaksperiodeTom = periode.vedtaksperiodeTom)

            arbeidssokerperiodeRepository.save(oppdatertPeriode)

            log.info(
                "Oppdaterte arbeidssokerperiode: ${opprinneligPeriode.id} med " +
                    "vedtaksperiodeId: ${opprinneligPeriode.vedtaksperiodeId} og " +
                    "tom-dato: ${opprinneligPeriode.vedtaksperiodeTom} til ny " +
                    "tom-dato: ${oppdatertPeriode.vedtaksperiodeTom}.",
            )
        }
    }

    data class PatchPeriode(
        val vedtaksperiodeId: String,
        val vedtaksperiodeTom: LocalDate,
    )
}
