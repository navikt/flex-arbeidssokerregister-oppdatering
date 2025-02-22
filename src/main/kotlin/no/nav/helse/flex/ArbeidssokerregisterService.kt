package no.nav.helse.flex

import no.nav.helse.flex.sykepengesoknad.kafka.SoknadstypeDTO
import no.nav.helse.flex.sykepengesoknad.kafka.SykepengesoknadDTO
import org.springframework.stereotype.Service

@Service
class ArbeidssokerregisterService(
    private val arbeidssokerregisterRepository: ArbeidssokerregisterRepository,
) {
    fun prosesserSoknad(soknad: SykepengesoknadDTO) {
        if (soknad.type == SoknadstypeDTO.FRISKMELDT_TIL_ARBEIDSFORMIDLING) {
            // TODO: Lagre det vi trenger for å konstruere databasen.
        }
    }
}
