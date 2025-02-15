package no.nav.helse.flex.paw

import no.nav.helse.flex.FellesTestOppsett
import okhttp3.mockwebserver.MockResponse
import org.amshove.kluent.`should be equal to`
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import java.time.OffsetDateTime

class ArbeidssokerregisterClientTest : FellesTestOppsett() {
    @Autowired
    private lateinit var arbeidssokerregisterClient: ArbeidssokerregisterClient

    @Test
    fun `Deserialiserer gyldig periode`() {
        val periode =
            "[{\"periodeId\":\"ec135a7e-f694-48fe-a65d-336fe7f923b1\",\"startet\":{\"tidspunkt\":\"2025-02-15T10:18:05.333Z\",\"utfoertAv\":{\"type\":\"SLUTTBRUKER\",\"id\":\"26830299946\"},\"kilde\":\"europe-north1-docker.pkg.dev/nais-management-233d/paw/paw-arbeidssokerregisteret-api-inngang:25.02.14.248-1\",\"aarsak\":\"Er over 18 år, er bosatt i Norge i henhold Folkeregisterloven\",\"tidspunktFraKilde\":null},\"avsluttet\":null}]"

        val mockResponse =
            MockResponse()
                .setBody(periode)
                .setHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
        arbeidssokerperiodeMockWebServer.enqueue(mockResponse)

        val response =
            arbeidssokerregisterClient.hentSisteArbeidssokerperiode(ArbeidssokerperiodeRequest("11111111111"))
        response.size `should be equal to` 1

        response.first().also {
            it.periodeId `should be equal to` "ec135a7e-f694-48fe-a65d-336fe7f923b1"
            it.startet.tidspunkt `should be equal to` OffsetDateTime.parse("2025-02-15T10:18:05.333Z")
            it.avsluttet `should be equal to` null
        }

        arbeidssokerperiodeMockWebServer.takeRequest().also {
            it.method `should be equal to` "POST"
            it.path `should be equal to` "/api/v1/veileder/arbeidssoekerperioder?siste=true"
            it.headers["Authorization"]!!.startsWith("Bearer") `should be equal to` true
        }
    }

    @Test
    fun `Deserialiserer avsluttet periode`() {
        val periode =
            "[{\"periodeId\":\"04a78565-a5ae-43ee-8e44-42893060995a\",\"startet\":{\"tidspunkt\":\"2025-02-11T15:08:03.278Z\",\"utfoertAv\":{\"type\":\"SLUTTBRUKER\",\"id\":\"04819696816\"},\"kilde\":\"europe-north1-docker.pkg.dev/nais-management-233d/paw/paw-arbeidssokerregisteret-api-inngang:25.02.11.242-1\",\"aarsak\":\"Er over 18 år, er bosatt i Norge i henhold Folkeregisterloven\",\"tidspunktFraKilde\":null},\"avsluttet\":{\"tidspunkt\":\"2025-02-13T09:42:19.254Z\",\"utfoertAv\":{\"type\":\"SYSTEM\",\"id\":\"europe-north1-docker.pkg.dev/nais-management-233d/paw/paw-arbeidssoekerregisteret-bekreftelse-utgang:25.02.13.147-1\"},\"kilde\":\"paw.arbeidssoekerregisteret.bekreftelse-utgang\",\"aarsak\":\"Graceperiode utløpt\",\"tidspunktFraKilde\":null}}]"

        val mockResponse =
            MockResponse()
                .setBody(periode)
                .setHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
        arbeidssokerperiodeMockWebServer.enqueue(mockResponse)

        val response =
            arbeidssokerregisterClient.hentSisteArbeidssokerperiode(ArbeidssokerperiodeRequest("11111111111"))
        response.size `should be equal to` 1

        response.first().also {
            it.periodeId `should be equal to` "04a78565-a5ae-43ee-8e44-42893060995a"
            it.startet.tidspunkt `should be equal to` OffsetDateTime.parse("2025-02-11T15:08:03.278Z")
            it.avsluttet!!.tidspunkt `should be equal to` OffsetDateTime.parse("2025-02-13T09:42:19.254Z")
        }
    }
}
