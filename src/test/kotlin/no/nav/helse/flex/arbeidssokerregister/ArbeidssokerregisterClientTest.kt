package no.nav.helse.flex.arbeidssokerregister

import io.kotest.matchers.shouldBe
import mockwebserver3.MockResponse
import no.nav.helse.flex.FNR
import no.nav.helse.flex.FellesTestOppsett
import org.junit.jupiter.api.Test
import java.time.Instant

class ArbeidssokerregisterClientTest : FellesTestOppsett() {
    @Test
    fun `Deserialiserer gyldig periode`() {
        val periode =
            """[{"periodeId":"ec135a7e-f694-48fe-a65d-336fe7f923b1","startet":{"tidspunkt":"2025-01-01T00:00:00.000Z","utfoertAv":{"type":"SLUTTBRUKER","id":"11111111111"},"kilde":"paw-arbeidssokerregisteret-api-inngang","aarsak":"Test","tidspunktFraKilde":null},"avsluttet":null}]"""

        arbeidssokerperiodeMockWebServer.enqueue(
            MockResponse.Builder().body(periode).build(),
        )

        arbeidssokerregisterClient.hentSisteArbeidssokerperiode(ArbeidssokerperiodeRequest(FNR)).single().also {
            it.periodeId shouldBe "ec135a7e-f694-48fe-a65d-336fe7f923b1"
            it.startet.tidspunkt shouldBe Instant.parse("2025-01-01T00:00:00.000Z")
            it.startet.aarsak shouldBe "Test"
            it.avsluttet shouldBe null
        }

        arbeidssokerperiodeMockWebServer.takeRequest().also {
            it.method shouldBe "POST"
            it.target shouldBe "/api/v1/veileder/arbeidssoekerperioder?siste=true"
            it.headers["Authorization"]!!.startsWith("Bearer") shouldBe true
        }
    }

    @Test
    fun `Deserialiserer avsluttet periode`() {
        val periode =
            """[{"periodeId":"04a78565-a5ae-43ee-8e44-42893060995a","startet":{"tidspunkt":"2025-01-01T00:00:00.000Z","utfoertAv":{"type":"SLUTTBRUKER","id":"11111111111"},"kilde":"paw-arbeidssokerregisteret-api-inngang","aarsak":"Test","tidspunktFraKilde":null},"avsluttet":{"tidspunkt":"2025-02-02T00:00:00.000Z","utfoertAv":{"type":"SYSTEM","id":"paw-arbeidssoekerregisteret-bekreftelse-utgang"},"kilde":"paw.arbeidssoekerregisteret.bekreftelse-utgang","aarsak":"Utløpt","tidspunktFraKilde":null}}]"""

        val mockResponse =
            MockResponse
                .Builder()
                .body(periode)
                .build()
        arbeidssokerperiodeMockWebServer.enqueue(mockResponse)

        val response =
            arbeidssokerregisterClient.hentSisteArbeidssokerperiode(ArbeidssokerperiodeRequest(FNR))
        response.size shouldBe 1

        response.single().also {
            it.periodeId shouldBe "04a78565-a5ae-43ee-8e44-42893060995a"
            it.startet.tidspunkt shouldBe Instant.parse("2025-01-01T00:00:00.000Z")
            it.startet.aarsak shouldBe "Test"
            it.avsluttet!!.tidspunkt shouldBe Instant.parse("2025-02-02T00:00:00.000Z")
            it.avsluttet.aarsak shouldBe "Utløpt"
        }
    }
}
