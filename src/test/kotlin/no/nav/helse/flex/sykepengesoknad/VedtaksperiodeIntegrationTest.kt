package no.nav.helse.flex.sykepengesoknad

import no.nav.helse.flex.FNR
import no.nav.helse.flex.FellesTestOppsett
import no.nav.helse.flex.VEDTAKSPERIODE_ID
import no.nav.helse.flex.arbeidssokerregister.ArbeidssokerperiodeResponse
import no.nav.helse.flex.arbeidssokerregister.BrukerResponse
import no.nav.helse.flex.arbeidssokerregister.FJORDEN_DAGER
import no.nav.helse.flex.arbeidssokerregister.KafkaKeyGeneratorResponse
import no.nav.helse.flex.arbeidssokerregister.MetadataResponse
import no.nav.helse.flex.lagSoknad
import no.nav.helse.flex.serialisertTilString
import no.nav.helse.flex.`should be within seconds of`
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadsstatusDTO
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadstypeDTO
import no.nav.paw.bekreftelse.paavegneav.v1.vo.Start
import okhttp3.mockwebserver.MockResponse
import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.`should not be equal to`
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import java.util.UUID

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class VedtaksperiodeIntegrationTest : FellesTestOppsett() {
    @AfterEach
    fun verifiserAtTopicErTomt() {
        arbeidssokerperiodeStoppConsumer.fetchRecords().size `should be equal to` 0
    }

    @AfterEach
    fun slettExceptions() {
        vedtaksperiodeExceptionRepository.deleteAll()
    }

    private val soknad = lagSoknad()

    @Test
    @Order(1)
    fun `Søknad med ny FriskTilArbeid vedtaksperiode blir lagret`() {
        val arbeidssokerperiodeId = UUID.randomUUID().toString()

        kafkaKeyGeneratorMockWebServer.enqueue(
            MockResponse().setBody(KafkaKeyGeneratorResponse(1000L).serialisertTilString()),
        )

        arbeidssokerperiodeMockWebServer.enqueue(
            MockResponse().setBody(lagArbeidsokerperiodeResponse(arbeidssokerperiodeId).serialisertTilString()),
        )

        sykepengesoknadService.behandleSoknad(soknad)

        arbeidssokerperiodeRepository.findAll().toList().single().also {
            it.vedtaksperiodeId `should be equal to` VEDTAKSPERIODE_ID
            it.kafkaRecordKey `should be equal to` 1000L
            it.arbeidssokerperiodeId `should be equal to` arbeidssokerperiodeId
            it.sendtPaaVegneAv!! `should be within seconds of` (1 to Instant.now())
        }

        kafkaKeyGeneratorMockWebServer.takeRequest() `should not be equal to` null
        arbeidssokerperiodeMockWebServer.takeRequest() `should not be equal to` null

        paaVegneAvConsumer.waitForRecords(1).single().also {
            it.key() `should be equal to` 1000L
            it.value().also {
                it.periodeId.toString() `should be equal to` arbeidssokerperiodeId
                (it.handling as Start).let { s ->
                    s.intervalMS `should be equal to` FJORDEN_DAGER
                    s.graceMS `should be equal to` 10368000000
                }
            }
        }
    }

    @Test
    @Order(2)
    fun `Søknad med kjent FriskTilArbeid vedtaksperiode blir ikke lagret`() {
        // Simulerer dobbel innsending av samme søknad.
        sykepengesoknadService.behandleSoknad(soknad)

        arbeidssokerperiodeRepository.findAll().toList().size `should be equal to` 1
    }

    @Test
    @Order(3)
    fun `Feil lagres når søknad har bruker med avsluttet arbeidssøkerperiode`() {
        kafkaKeyGeneratorMockWebServer.enqueue(
            MockResponse().setBody(KafkaKeyGeneratorResponse(1000L).serialisertTilString()),
        )

        arbeidssokerperiodeMockWebServer.enqueue(
            MockResponse().setBody(
                lagArbeidsokerperiodeResponse(
                    arbeidssokerperiodeId = UUID.randomUUID().toString(),
                    erAvsluttet = true,
                ).serialisertTilString(),
            ),
        )

        val vedtaksperiodeId = UUID.randomUUID().toString()
        sykepengesoknadService.behandleSoknad(
            soknad.copy(
                fnr = "22222222222",
                friskTilArbeidVedtakId = vedtaksperiodeId,
            ),
        )

        vedtaksperiodeExceptionRepository.findAll().single().also {
            it.vedtaksperiodeId `should be equal to` vedtaksperiodeId
            it.sykepengesoknadId `should be equal to` soknad.id
            it.fnr `should be equal to` "22222222222"
            it.exceptionClassName `should be equal to` "no.nav.helse.flex.sykepengesoknad.ArbeidssokerperiodeException"
        }

        arbeidssokerperiodeRepository.findAll().toList().size `should be equal to` 1

        kafkaKeyGeneratorMockWebServer.takeRequest() `should not be equal to` null
        arbeidssokerperiodeMockWebServer.takeRequest() `should not be equal to` null
    }

    @Test
    @Order(3)
    fun `Feil lagres når søknad har bruker som ikke er registert i arbeidssøkerregiseret`() {
        kafkaKeyGeneratorMockWebServer.enqueue(
            MockResponse().setBody(KafkaKeyGeneratorResponse(1000L).serialisertTilString()),
        )

        arbeidssokerperiodeMockWebServer.enqueue(
            MockResponse().setResponseCode(200).setBody("[]"),
        )

        val vedtaksperiodeId = UUID.randomUUID().toString()
        sykepengesoknadService.behandleSoknad(
            soknad.copy(
                fnr = "22222222222",
                friskTilArbeidVedtakId = vedtaksperiodeId,
            ),
        )

        vedtaksperiodeExceptionRepository.findAll().single().also {
            it.vedtaksperiodeId `should be equal to` vedtaksperiodeId
            it.sykepengesoknadId `should be equal to` soknad.id
            it.fnr `should be equal to` "22222222222"
            it.exceptionClassName `should be equal to` "no.nav.helse.flex.sykepengesoknad.ArbeidssokerperiodeException"
        }

        arbeidssokerperiodeRepository.findAll().toList().size `should be equal to` 1

        kafkaKeyGeneratorMockWebServer.takeRequest() `should not be equal to` null
        arbeidssokerperiodeMockWebServer.takeRequest() `should not be equal to` null
    }

    @Test
    @Order(4)
    fun `Kun søknad med status FREMTIDIG blir behandlet`() {
        arbeidssokerperiodeRepository.deleteAll()

        sykepengesoknadService.behandleSoknad(soknad.copy(status = SoknadsstatusDTO.NY))

        arbeidssokerperiodeRepository.findAll().toList().size `should be equal to` 0
    }

    @Test
    @Order(4)
    fun `Kun søknad med type FRISKMELDT_TIL_ARBEIDSFORMIDLING blir behandlet`() {
        arbeidssokerperiodeRepository.deleteAll()

        sykepengesoknadService.behandleSoknad(soknad.copy(type = SoknadstypeDTO.ARBEIDSTAKERE))

        arbeidssokerperiodeRepository.findAll().toList().size `should be equal to` 0
    }

    @Test
    @Order(4)
    fun `Søknad som mangler vedtaksperiodeId feiler`() {
        arbeidssokerperiodeRepository.deleteAll()

        assertThrows<Exception> {
            sykepengesoknadService.behandleSoknad(soknad.copy(friskTilArbeidVedtakId = null))
        }

        arbeidssokerperiodeRepository.findAll().toList().size `should be equal to` 0
    }

    @Test
    @Order(4)
    fun `Søknad som mangler friskTilArbeidVedtakPeriode feiler`() {
        arbeidssokerperiodeRepository.deleteAll()

        assertThrows<Exception> {
            sykepengesoknadService.behandleSoknad(soknad.copy(friskTilArbeidVedtakPeriode = null))
        }

        arbeidssokerperiodeRepository.findAll().toList().size `should be equal to` 0
    }

    private fun lagArbeidsokerperiodeResponse(
        arbeidssokerperiodeId: String,
        erAvsluttet: Boolean = false,
    ): List<ArbeidssokerperiodeResponse> {
        val avsluttet =
            MetadataResponse(
                tidspunkt = Instant.parse("2025-01-31T00:00:00.000Z"),
                utfoertAv =
                    BrukerResponse(
                        type = "SYSTEM",
                        id = "paw-arbeidssoekerregisteret-bekreftelse-utgang",
                    ),
                kilde = "paw-arbeidssoekerregisteret-bekreftelse-utgang",
                aarsak = "Test",
                tidspunktFraKilde = null,
            )

        return listOf(
            ArbeidssokerperiodeResponse(
                periodeId = arbeidssokerperiodeId,
                startet =
                    MetadataResponse(
                        tidspunkt = Instant.parse("2025-01-01T00:00:00.000Z"),
                        utfoertAv =
                            BrukerResponse(
                                type = "SLUTTBRUKER",
                                id = FNR,
                            ),
                        kilde = "paw-arbeidssokerregisteret-api-inngang",
                        aarsak = "Test",
                        tidspunktFraKilde = null,
                    ),
                avsluttet = if (erAvsluttet) avsluttet else null,
            ),
        )
    }
}
