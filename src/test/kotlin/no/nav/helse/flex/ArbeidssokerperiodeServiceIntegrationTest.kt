package no.nav.helse.flex

import no.nav.helse.flex.arbeidssokerregister.ARBEIDSSOKERPERIODE_PAA_VEGNE_AV_TOPIC
import no.nav.helse.flex.arbeidssokerregister.ArbeidssokerperiodeResponse
import no.nav.helse.flex.arbeidssokerregister.BrukerResponse
import no.nav.helse.flex.arbeidssokerregister.FIRE_MAANEDER
import no.nav.helse.flex.arbeidssokerregister.FJORDEN_DAGER
import no.nav.helse.flex.arbeidssokerregister.KafkaKeyGeneratorResponse
import no.nav.helse.flex.arbeidssokerregister.MetadataResponse
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadsstatusDTO
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadstypeDTO
import no.nav.paw.bekreftelse.paavegneav.v1.PaaVegneAv
import no.nav.paw.bekreftelse.paavegneav.v1.vo.Start
import okhttp3.mockwebserver.MockResponse
import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.`should not be equal to`
import org.apache.kafka.clients.consumer.Consumer
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import java.time.OffsetDateTime
import java.util.*

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class ArbeidssokerperiodeServiceIntegrationTest : FellesTestOppsett() {
    @Autowired
    private lateinit var arbeidssokerperiodeService: ArbeidssokerperiodeService

    @Autowired
    private lateinit var arbeidssokerperiodeRepository: ArbeidssokerperiodeRepository

    @Autowired
    private lateinit var paaVegneAvConsumer: Consumer<Long, PaaVegneAv>

    @BeforeAll
    fun slettFraDatabase() {
        arbeidssokerperiodeRepository.deleteAll()
    }

    @BeforeAll
    fun subscribeToTopics() {
        paaVegneAvConsumer.subscribeToTopics(ARBEIDSSOKERPERIODE_PAA_VEGNE_AV_TOPIC)
    }

    private val soknad = lagFremtidigFriskTilArbeidSoknad()

    @Test
    @Order(1)
    fun `Søknad med ukjent FriskTilArbeid vedtaksperiode blir lagret`() {
        kafkaKeyGeneratorMockWebServer.enqueue(
            MockResponse().setBody(KafkaKeyGeneratorResponse(1000L).serialisertTilString()),
        )

        val arbeidssokerperiodeId = UUID.randomUUID().toString()
        arbeidssokerperiodeMockWebServer.enqueue(
            MockResponse().setBody(lagArbeidsokerperiodeResponse(arbeidssokerperiodeId).serialisertTilString()),
        )

        soknad.also {
            arbeidssokerperiodeService.behandleSoknad(it)
        }

        arbeidssokerperiodeRepository.findAll().toList().also {
            it.size `should be equal to` 1
            it.first().also {
                it.vedtaksperiodeId `should be equal to` VEDTAKSPERIODE_ID
                it.kafkaRecordKey `should be equal to` 1000L
                it.arbeidssokerperiodeId `should be equal to` arbeidssokerperiodeId
                it.sendtPaaVegneAv `should not be equal to` null
            }
        }

        kafkaKeyGeneratorMockWebServer.takeRequest() `should not be equal to` null
        arbeidssokerperiodeMockWebServer.takeRequest() `should not be equal to` null

        paaVegneAvConsumer.waitForRecords(1).first().also {
            it.key() `should be equal to` 1000L
            it.value().also {
                it.periodeId.toString() `should be equal to` arbeidssokerperiodeId
                (it.handling as Start).let { s ->
                    s.intervalMS `should be equal to` FJORDEN_DAGER
                    s.graceMS `should be equal to` FIRE_MAANEDER
                }
            }
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

    private fun lagArbeidsokerperiodeResponse(periodeId: String): List<ArbeidssokerperiodeResponse> =
        listOf(
            ArbeidssokerperiodeResponse(
                periodeId = periodeId,
                startet =
                    MetadataResponse(
                        tidspunkt = OffsetDateTime.parse("2025-01-01T00:00:00.000Z"),
                        utfoertAv =
                            BrukerResponse(
                                type = "SLUTTBRUKER",
                                id = "11111111111",
                            ),
                        kilde = "paw-arbeidssokerregisteret-api-inngang",
                        aarsak = "Test",
                        tidspunktFraKilde = null,
                    ),
                avsluttet = null,
            ),
        )
}
