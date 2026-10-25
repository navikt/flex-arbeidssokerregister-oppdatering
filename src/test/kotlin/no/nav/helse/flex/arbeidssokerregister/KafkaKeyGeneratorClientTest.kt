package no.nav.helse.flex.arbeidssokerregister

import io.kotest.matchers.shouldBe
import mockwebserver3.MockResponse
import no.nav.helse.flex.FNR
import no.nav.helse.flex.FellesTestOppsett
import no.nav.helse.flex.serialisertTilString
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.HttpServerErrorException

class KafkaKeyGeneratorClientTest : FellesTestOppsett() {
    @Test
    fun `Hent Kafka Record Key`() {
        kafkaKeyGeneratorMockWebServer.enqueue(
            MockResponse.Builder().body(KafkaKeyGeneratorResponse(1000L).serialisertTilString()).build(),
        )

        kafkaKeyGeneratorClient.hentKafkaKey(KafkaKeyGeneratorRequest(FNR))!!.key shouldBe 1000

        kafkaKeyGeneratorMockWebServer.takeRequest().also {
            it.method shouldBe "POST"
            it.target shouldBe "/api/v1/record-key"
            it.headers["Authorization"]!!.startsWith("Bearer") shouldBe true
        }
    }

    @Test
    fun `Kaster HttpClientErrorException når ressurs ikke finnes`() {
        kafkaKeyGeneratorMockWebServer.enqueue(MockResponse.Builder().code(404).build())

        assertThrows<HttpClientErrorException> {
            kafkaKeyGeneratorClient.hentKafkaKey(KafkaKeyGeneratorRequest(FNR))
        }
    }

    @Test
    fun `Kaster HttpServerErrorException ved server feil`() {
        kafkaKeyGeneratorMockWebServer.enqueue(MockResponse.Builder().code(500).build())

        assertThrows<HttpServerErrorException> {
            kafkaKeyGeneratorClient.hentKafkaKey(KafkaKeyGeneratorRequest(FNR))
        }
    }
}
