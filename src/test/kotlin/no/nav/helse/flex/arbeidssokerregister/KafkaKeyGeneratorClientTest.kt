package no.nav.helse.flex.arbeidssokerregister

import mockwebserver3.MockResponse
import no.nav.helse.flex.FNR
import no.nav.helse.flex.FellesTestOppsett
import no.nav.helse.flex.serialisertTilString
import org.amshove.kluent.`should be equal to`
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

        kafkaKeyGeneratorClient.hentKafkaKey(KafkaKeyGeneratorRequest(FNR))!!.key `should be equal to` 1000

        kafkaKeyGeneratorMockWebServer.takeRequest().also {
            it.method `should be equal to` "POST"
            it.target `should be equal to` "/api/v1/record-key"
            it.headers["Authorization"]!!.startsWith("Bearer") `should be equal to` true
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
