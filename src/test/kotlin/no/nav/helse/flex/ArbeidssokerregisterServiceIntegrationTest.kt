package no.nav.helse.flex

import no.nav.helse.flex.sykepengesoknad.kafka.SoknadsstatusDTO
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadstypeDTO
import org.amshove.kluent.`should be equal to`
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class ArbeidssokerregisterServiceIntegrationTest : FellesTestOppsett() {
    @Autowired
    private lateinit var arbeidssokerperiodeService: ArbeidssokerperiodeService

    @Autowired
    private lateinit var arbeidssokerperiodeRepository: ArbeidssokerperiodeRepository

    @BeforeAll
    fun slettFraDatabase() {
        arbeidssokerperiodeRepository.deleteAll()
    }

    private val soknad = lagFremtidigFriskTilArbeidSoknad()

    @Test
    @Order(1)
    fun `Søknad med ukjent FriskTilArbeid vedtaksperiode blir lagret`() {
        soknad.also {
            arbeidssokerperiodeService.behandleSoknad(it)
        }

        arbeidssokerperiodeRepository.findAll().toList().also {
            it.size `should be equal to` 1
            it.first().vedtaksperiodeId `should be equal to` VEDTAKSPERIODE_ID
        }
    }

    @Test
    @Order(2)
    fun `Søknad med kjent FriskTilArbeid vedtaksperiode blir ikke lagret`() {
        soknad.also {
            arbeidssokerperiodeService.behandleSoknad(it)
        }

        arbeidssokerperiodeRepository.findAll().toList().size `should be equal to` 1
    }

    @Test
    fun `Kun søknad med status FREMTIDIG blir behandlet`() {
        arbeidssokerperiodeRepository.deleteAll()

        soknad.also {
            arbeidssokerperiodeService.behandleSoknad(it.copy(status = SoknadsstatusDTO.NY))
        }

        arbeidssokerperiodeRepository.findAll().toList().size `should be equal to` 0
    }

    @Test
    fun `Kun søknad med type FRISKMELDT_TIL_ARBEIDSFORMIDLING blir behandlet`() {
        arbeidssokerperiodeRepository.deleteAll()

        soknad.also {
            arbeidssokerperiodeService.behandleSoknad(it.copy(type = SoknadstypeDTO.ARBEIDSTAKERE))
        }

        arbeidssokerperiodeRepository.findAll().toList().size `should be equal to` 0
    }

    @Test
    fun `Søknad som mangler vedtaksperiodeId feiler`() {
        arbeidssokerperiodeRepository.deleteAll()

        assertThrows<Exception> {
            soknad.also {
                arbeidssokerperiodeService.behandleSoknad(it.copy(friskTilArbeidVedtakId = null))
            }
        }

        arbeidssokerperiodeRepository.findAll().toList().size `should be equal to` 0
    }

    @Test
    fun `Søknad som mangler friskTilArbeidVedtakPeriode feiler`() {
        arbeidssokerperiodeRepository.deleteAll()

        assertThrows<Exception> {
            soknad.also {
                arbeidssokerperiodeService.behandleSoknad(it.copy(friskTilArbeidVedtakPeriode = null))
            }
        }

        arbeidssokerperiodeRepository.findAll().toList().size `should be equal to` 0
    }
}
