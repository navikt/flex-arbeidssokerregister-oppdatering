package no.nav.helse.flex.arbeidssokerregister

import no.nav.helse.flex.FNR
import no.nav.helse.flex.FellesTestOppsett
import okhttp3.mockwebserver.MockResponse
import org.amshove.kluent.`should be equal to`
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.Instant

class ArbeidssokerregisterClientTest : FellesTestOppsett() {
    @Autowired
    private lateinit var arbeidssokerregisterClient: ArbeidssokerregisterClient

    @Test
    fun `Deserialiserer gyldig periode`() {
        val periode =
            """[{"periodeId":"ec135a7e-f694-48fe-a65d-336fe7f923b1","startet":{"tidspunkt":"2025-01-01T00:00:00.000Z","utfoertAv":{"type":"SLUTTBRUKER","id":"11111111111"},"kilde":"paw-arbeidssokerregisteret-api-inngang","aarsak":"Test","tidspunktFraKilde":null},"avsluttet":null}]"""

        arbeidssokerperiodeMockWebServer.enqueue(
            MockResponse().setBody(periode),
        )

        arbeidssokerregisterClient.hentSisteArbeidssokerperiode(ArbeidssokerperiodeRequest(FNR)).single().also {
            it.periodeId `should be equal to` "ec135a7e-f694-48fe-a65d-336fe7f923b1"
            it.startet.tidspunkt `should be equal to` Instant.parse("2025-01-01T00:00:00.000Z")
            it.startet.aarsak `should be equal to` "Test"
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
            """[{"periodeId":"04a78565-a5ae-43ee-8e44-42893060995a","startet":{"tidspunkt":"2025-01-01T00:00:00.000Z","utfoertAv":{"type":"SLUTTBRUKER","id":"11111111111"},"kilde":"paw-arbeidssokerregisteret-api-inngang","aarsak":"Test","tidspunktFraKilde":null},"avsluttet":{"tidspunkt":"2025-02-02T00:00:00.000Z","utfoertAv":{"type":"SYSTEM","id":"paw-arbeidssoekerregisteret-bekreftelse-utgang"},"kilde":"paw.arbeidssoekerregisteret.bekreftelse-utgang","aarsak":"Utløpt","tidspunktFraKilde":null}}]"""

        val mockResponse =
            MockResponse()
                .setBody(periode)
        arbeidssokerperiodeMockWebServer.enqueue(mockResponse)

        val response =
            arbeidssokerregisterClient.hentSisteArbeidssokerperiode(ArbeidssokerperiodeRequest(FNR))
        response.size `should be equal to` 1

        response.single().also {
            it.periodeId `should be equal to` "04a78565-a5ae-43ee-8e44-42893060995a"
            it.startet.tidspunkt `should be equal to` Instant.parse("2025-01-01T00:00:00.000Z")
            it.startet.aarsak `should be equal to` "Test"
            it.avsluttet!!.tidspunkt `should be equal to` Instant.parse("2025-02-02T00:00:00.000Z")
            it.avsluttet.aarsak `should be equal to` "Utløpt"
        }
    }
}
