package no.nav.helse.flex

import no.nav.helse.flex.arbeidssokerperiode.ArbeidssokerperiodeRepository
import no.nav.helse.flex.sykepengesoknad.PeriodebekreftelseRepository
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

@Component
class SlettSlettede(
    private val periodebekreftelseRepository: PeriodebekreftelseRepository,
    private val arbeidssokerperiodeRepository: ArbeidssokerperiodeRepository,
) {
    private val log = logger()

    @Scheduled(initialDelay = 3, fixedDelay = 3600, timeUnit = TimeUnit.MINUTES)
    fun slettSlettede() {
        listOf(
            "4fe42342-7102-44d8-acd9-dc6a4f226f5a",
            "e4504199-f052-469a-9e0d-bffd1bad6bef",
            "688142df-92d9-4f44-b176-fd74d0c5da1d",
        ).forEach {
            periodebekreftelseRepository.deleteByArbeidssokerperiodeId(it)
            arbeidssokerperiodeRepository.deleteByVedtaksperiodeId(it)
            log.info("Slettet arbeidssokerperiode og periodebekreftelse for vedtaksId: $it.")
        }
    }
}
