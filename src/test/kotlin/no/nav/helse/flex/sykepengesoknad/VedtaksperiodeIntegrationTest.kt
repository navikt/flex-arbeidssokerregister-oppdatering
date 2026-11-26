package no.nav.helse.flex.sykepengesoknad

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import mockwebserver3.MockResponse
import no.nav.helse.flex.FNR
import no.nav.helse.flex.FellesTestOppsett
import no.nav.helse.flex.VEDTAKSPERIODE_ID
import no.nav.helse.flex.arbeidssokerperiode.Arbeidssokerperiode
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
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

class VedtaksperiodeIntegrationTest : FellesTestOppsett() {
    @BeforeEach
    @AfterEach
    fun resetMellomTester() {
        slettFraDatabase()
    }

    @AfterEach
    fun verifiserAtTopicErTomt() {
        arbeidssokerperiodeStartStoppConsumer.fetchRecords().size shouldBe 0
    }

    private val periodeFomTom = Periode(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 31))
    private val soknad = lagSoknad(periodeFomTom = periodeFomTom)

    @Test
    fun `Søknad med med ignorerArbeidssokerregister satt blir ikke behandlet`() {
        val soknad = lagSoknad().copy(ignorerArbeidssokerregister = true)

        sykepengesoknadService.behandleSoknad(soknad)

        arbeidssokerperiodeRepository.findAll().toList() shouldHaveSize 0
    }

    @Test
    fun `Søknad med ny FriskTilArbeid vedtaksperiode blir lagret`() {
        val arbeidssokerperiodeId = UUID.randomUUID().toString()

        kafkaKeyGeneratorMockWebServer.enqueue(
            MockResponse.Builder().body(KafkaKeyGeneratorResponse(1000L).serialisertTilString()).build(),
        )

        arbeidssokerperiodeMockWebServer.enqueue(
            MockResponse.Builder().body(lagArbeidsokerperiodeResponse(arbeidssokerperiodeId).serialisertTilString()).build(),
        )

        sykepengesoknadService.behandleSoknad(soknad)

        arbeidssokerperiodeRepository.findAll().single().also {
            it.vedtaksperiodeId shouldBe VEDTAKSPERIODE_ID
            it.kafkaRecordKey shouldBe 1000L
            it.arbeidssokerperiodeId shouldBe arbeidssokerperiodeId
            it.sendtPaaVegneAv!! `should be within seconds of` (1 to Instant.now())
        }

        kafkaKeyGeneratorMockWebServer.takeRequest() shouldNotBe null
        arbeidssokerperiodeMockWebServer.takeRequest() shouldNotBe null

        paaVegneAvConsumer.waitForRecords(1).single().also {
            it.key() shouldBe 1000L
            it.value().also {
                it.periodeId.toString() shouldBe arbeidssokerperiodeId
                (it.handling as Start).let { s ->
                    s.intervalMS shouldBe FJORDEN_DAGER
                    s.graceMS shouldBe 10368000000
                }
            }
        }
    }

    @Test
    fun `Søknad med kjent FriskTilArbeid vedtaksperiode blir ikke lagret`() {
        val lagretPeriode = lagreArbeidssokerperiode()

        sykepengesoknadService.behandleSoknad(soknad)

        arbeidssokerperiodeRepository.findAll().single().also {
            it.id shouldBe lagretPeriode.id
        }
    }

    @Test
    fun `Kun søknad med status FREMTIDIG blir behandlet`() {
        sykepengesoknadService.behandleSoknad(soknad.copy(status = SoknadsstatusDTO.NY))

        arbeidssokerperiodeRepository.findAll().toList().size shouldBe 0
    }

    @Test
    fun `Kun søknad med type FRISKMELDT_TIL_ARBEIDSFORMIDLING blir behandlet`() {
        sykepengesoknadService.behandleSoknad(soknad.copy(type = SoknadstypeDTO.ARBEIDSTAKERE))

        arbeidssokerperiodeRepository.findAll().toList().size shouldBe 0
    }

    @Test
    fun `Søknad som mangler vedtaksperiodeId feiler`() {
        assertThrows<Exception> {
            sykepengesoknadService.behandleSoknad(soknad.copy(friskTilArbeidVedtakId = null))
        }

        arbeidssokerperiodeRepository.findAll().toList().size shouldBe 0
    }

    @Test
    fun `Søknad som mangler friskTilArbeidVedtakPeriode feiler`() {
        assertThrows<Exception> {
            sykepengesoknadService.behandleSoknad(soknad.copy(friskTilArbeidVedtakPeriode = null))
        }

        arbeidssokerperiodeRepository.findAll().toList().size shouldBe 0
    }

    private fun lagreArbeidssokerperiode(): Arbeidssokerperiode =
        arbeidssokerperiodeRepository.save(
            Arbeidssokerperiode(
                fnr = FNR,
                vedtaksperiodeId = VEDTAKSPERIODE_ID,
                vedtaksperiodeFom = periodeFomTom.fom,
                vedtaksperiodeTom = periodeFomTom.tom,
                opprettet = Instant.now(),
                kafkaRecordKey = 1000L,
                arbeidssokerperiodeId = UUID.randomUUID().toString(),
                sendtPaaVegneAv = Instant.now(),
            ),
        )

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
