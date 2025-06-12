package no.nav.helse.flex.api

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.helse.flex.FNR
import no.nav.helse.flex.FellesTestOppsett
import no.nav.helse.flex.arbeidssokerperiode.Arbeidssokerperiode
import no.nav.helse.flex.arbeidssokerperiode.AvsluttetAarsak
import no.nav.helse.flex.objectMapper
import no.nav.helse.flex.serialisertTilString
import no.nav.helse.flex.sykepengesoknad.Periodebekreftelse
import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.security.mock.oauth2.token.DefaultOAuth2TokenCallback
import org.amshove.kluent.`should be equal to`
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.*

class FlexInternalControllerIntegrationTest : FellesTestOppsett() {
    private val vedtaksperiodeId = UUID.randomUUID().toString()

    @Autowired
    private lateinit var mockMvc: MockMvc

    @AfterEach
    fun nullstillDatabaseEtterHverTest() {
        slettFraDatabase()
    }

    @Test
    fun `Returnerer arbeidssokerperiode med periodebekreftelser`() {
        val lagretArbeidssokerperiode =
            arbeidssokerperiodeRepository.save(
                Arbeidssokerperiode(
                    fnr = FNR,
                    vedtaksperiodeId = vedtaksperiodeId,
                    vedtaksperiodeFom = LocalDate.now().minusMonths(1),
                    vedtaksperiodeTom = LocalDate.now().plusMonths(2),
                    opprettet = Instant.now(),
                ),
            )

        repeat(2) {
            periodebekreftelseRepository.save(
                Periodebekreftelse(
                    arbeidssokerperiodeId = lagretArbeidssokerperiode.id!!,
                    sykepengesoknadId = UUID.randomUUID().toString(),
                    fortsattArbeidssoker = true,
                    inntektUnderveis = false,
                    opprettet = Instant.now(),
                ),
            )
        }

        objectMapper.readValue<FlexInternalResponse>(hentArbeidssokerperioder()).also {
            it.arbeidssokerperioder.single().also {
                it.vedtaksperiodeId `should be equal to` vedtaksperiodeId
                it.periodebekreftelser!!.forEach {
                    it.arbeidssokerperiodeId `should be equal to` lagretArbeidssokerperiode.id!!
                }
            }
        }
    }

    @Test
    fun `Returnerer arbeidssokerperiode uten periodebekreftelser`() {
        arbeidssokerperiodeRepository.save(
            Arbeidssokerperiode(
                fnr = FNR,
                vedtaksperiodeId = vedtaksperiodeId,
                vedtaksperiodeFom = LocalDate.now().minusMonths(1),
                vedtaksperiodeTom = LocalDate.now().plusMonths(2),
                opprettet = Instant.now(),
            ),
        )

        objectMapper.readValue<FlexInternalResponse>(hentArbeidssokerperioder()).also {
            it.arbeidssokerperioder.single().also {
                it.vedtaksperiodeId `should be equal to` vedtaksperiodeId
                it.periodebekreftelser!!.size `should be equal to` 0
            }
        }
    }

    @Test
    fun `Map til ArbeidssokerperiodeResponse`() {
        val arbeidssokerperiodeId = UUID.randomUUID().toString()
        val sykepengesoknadId = UUID.randomUUID().toString()
        val periodebekreftelseId = UUID.randomUUID().toString()
        val arbeidssokerregisterPeriodeId = UUID.randomUUID().toString()
        val periodeBekreftelseOpprettet = LocalDate.of(2020, 4, 1).atStartOfDay().toInstant(ZoneOffset.UTC)
        val arbeidssokerperiodeOpprettet = LocalDate.of(2020, 4, 2).atStartOfDay().toInstant(ZoneOffset.UTC)
        val sendtPaaVegneAv = LocalDate.of(2020, 4, 3).atStartOfDay().toInstant(ZoneOffset.UTC)
        val avsluttetMottatt = LocalDate.of(2020, 4, 4).atStartOfDay().toInstant(ZoneOffset.UTC)
        val avsluttetTidspunkt = LocalDate.of(2020, 4, 5).atStartOfDay().toInstant(ZoneOffset.UTC)
        val sendtAvsluttet = LocalDate.of(2020, 4, 6).atStartOfDay().toInstant(ZoneOffset.UTC)

        val periodebekreftelseResponse =
            Periodebekreftelse(
                id = periodebekreftelseId,
                arbeidssokerperiodeId = arbeidssokerperiodeId,
                sykepengesoknadId = sykepengesoknadId,
                fortsattArbeidssoker = true,
                inntektUnderveis = true,
                opprettet = periodeBekreftelseOpprettet,
                avsluttendeSoknad = true,
            ).tilPeriodebekreftelseResponse().also {
                it.arbeidssokerperiodeId `should be equal to` arbeidssokerperiodeId
                it.sykepengesoknadId `should be equal to` sykepengesoknadId
                it.fortsattArbeidssoker `should be equal to` true
                it.inntektUnderveis `should be equal to` true
                it.opprettet `should be equal to` periodeBekreftelseOpprettet
                it.avsluttendeSoknad `should be equal to` true
            }

        Arbeidssokerperiode(
            id = arbeidssokerperiodeId,
            fnr = FNR,
            vedtaksperiodeId = vedtaksperiodeId,
            vedtaksperiodeFom = LocalDate.now().minusMonths(1),
            vedtaksperiodeTom = LocalDate.now().plusMonths(2),
            opprettet = arbeidssokerperiodeOpprettet,
            kafkaRecordKey = 1L,
            arbeidssokerperiodeId = arbeidssokerregisterPeriodeId,
            sendtPaaVegneAv = sendtPaaVegneAv,
            avsluttetMottatt = avsluttetMottatt,
            avsluttetTidspunkt = avsluttetTidspunkt,
            sendtAvsluttet = sendtAvsluttet,
            avsluttetAarsak = AvsluttetAarsak.AVSLUTTET_PERIODE,
        ).tilArbeidssokerperiodeResponse(listOf(periodebekreftelseResponse)).also {
            it.id `should be equal to` arbeidssokerperiodeId
            it.fnr `should be equal to` FNR
            it.vedtaksperiodeId `should be equal to` vedtaksperiodeId
            it.vedtaksperiodeFom `should be equal to` LocalDate.now().minusMonths(1)
            it.vedtaksperiodeTom `should be equal to` LocalDate.now().plusMonths(2)
            it.opprettet `should be equal to` arbeidssokerperiodeOpprettet
            it.arbeidssokerperiodeId `should be equal to` arbeidssokerregisterPeriodeId
            it.sendtPaaVegneAv `should be equal to` sendtPaaVegneAv
            it.avsluttetMottatt `should be equal to` avsluttetMottatt
            it.avsluttetTidspunkt `should be equal to` avsluttetTidspunkt
            it.sendtAvsluttet `should be equal to` sendtAvsluttet
            it.avsluttetAarsak `should be equal to` AvsluttetAarsak.AVSLUTTET_PERIODE
            it.periodebekreftelser!!.single() `should be equal to` periodebekreftelseResponse
        }
    }

    @Test
    fun `Oppdaterer vedtaksperiodeTom for en arbeidssokerperiode`() {
        val initialTomDate = LocalDate.now().plusMonths(2)
        val newTomDate = LocalDate.now().plusMonths(3)

        val lagretArbeidssokerperiode =
            arbeidssokerperiodeRepository.save(
                Arbeidssokerperiode(
                    fnr = FNR,
                    vedtaksperiodeId = vedtaksperiodeId,
                    vedtaksperiodeFom = LocalDate.now().minusMonths(1),
                    vedtaksperiodeTom = initialTomDate,
                    opprettet = Instant.now(),
                ),
            )

        val updateRequest =
            UpdateVedtaksperiodeTomRequest(
                id = lagretArbeidssokerperiode.id!!,
                vedtaksperiodeTom = newTomDate,
            )

        oppdaterArbeidssokerperiodeTom(updateRequest)

        val oppdatertArbeidssokerperiode = arbeidssokerperiodeRepository.findById(lagretArbeidssokerperiode.id!!).get()
        oppdatertArbeidssokerperiode.vedtaksperiodeTom `should be equal to` newTomDate
    }

    @Test
    fun `Returnerer 404 når arbeidssokerperiode ikke finnes`() {
        val nonExistentId = UUID.randomUUID().toString()
        val updateRequest =
            UpdateVedtaksperiodeTomRequest(
                id = nonExistentId,
                vedtaksperiodeTom = LocalDate.now().plusMonths(3),
            )

        mockMvc
            .perform(
                MockMvcRequestBuilders
                    .put("/api/v1/flex/arbeidssokerperioder/oppdater-tom")
                    .header("Authorization", "Bearer ${skapAzureJwt("flex-internal-frontend-client-id", "99999999999")}")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(updateRequest.serialisertTilString()),
            ).andExpect(MockMvcResultMatchers.status().isNotFound)
    }

    private fun hentArbeidssokerperioder(): String =
        mockMvc
            .perform(lagMockMvcRequest())
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andReturn()
            .response.contentAsString

    private fun oppdaterArbeidssokerperiodeTom(request: UpdateVedtaksperiodeTomRequest) {
        mockMvc
            .perform(
                MockMvcRequestBuilders
                    .put("/api/v1/flex/arbeidssokerperioder/oppdater-tom")
                    .header("Authorization", "Bearer ${skapAzureJwt("flex-internal-frontend-client-id", "99999999999")}")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(request.serialisertTilString()),
            ).andExpect(MockMvcResultMatchers.status().isNoContent)
    }

    private fun lagMockMvcRequest(): MockHttpServletRequestBuilder =
        MockMvcRequestBuilders
            .post("/api/v1/flex/arbeidssokerperioder")
            .header("Authorization", "Bearer ${skapAzureJwt("flex-internal-frontend-client-id", "99999999999")}")
            .contentType(MediaType.APPLICATION_JSON)
            .content(FlexInternalRequest(FNR).serialisertTilString())
}

fun FellesTestOppsett.skapAzureJwt(
    subject: String,
    navIdent: String,
) = buildAzureClaimSet(
    subject = subject,
    claims = hashMapOf("NAVident" to navIdent),
)

fun FellesTestOppsett.buildAzureClaimSet(
    subject: String,
    issuer: String = "azureator",
    audience: String = "flex-arbeidssokerregister-oppdatering-client-id",
    claims: HashMap<String, String> = hashMapOf(),
): String =
    server.token(
        subject = "Test",
        issuerId = issuer,
        clientId = subject,
        audience = audience,
        claims = claims,
    )

fun MockOAuth2Server.token(
    subject: String,
    issuerId: String = "selvbetjening",
    clientId: String = UUID.randomUUID().toString(),
    audience: String = "loginservice-client-id",
    claims: Map<String, Any> = mapOf("acr" to "Level4"),
): String =
    this
        .issueToken(
            issuerId,
            clientId,
            DefaultOAuth2TokenCallback(
                issuerId = issuerId,
                subject = subject,
                audience = listOf(audience),
                claims = claims,
                expiry = 3600,
            ),
        ).serialize()
