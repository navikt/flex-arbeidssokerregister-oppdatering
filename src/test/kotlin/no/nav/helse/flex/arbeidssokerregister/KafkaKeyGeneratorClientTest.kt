package no.nav.helse.flex.arbeidssokerregister

import no.nav.helse.flex.FellesTestOppsett
import no.nav.helse.flex.serialisertTilString
import okhttp3.mockwebserver.MockResponse
import org.amshove.kluent.`should be equal to`
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.HttpServerErrorException

class KafkaKeyGeneratorClientTest : FellesTestOppsett() {
    @Autowired
    private lateinit var kafkaKeyGeneratorClient: KafkaKeyGeneratorClient

    @Test
    fun `Hent Kafka Record Key`() {
        val mockResponse =
            MockResponse()
                .setBody(KafkaKeyGeneratorResponse(1000).serialisertTilString())
                .setHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
        kafkaKeyGeneratorMockWebServer.enqueue(mockResponse)

        kafkaKeyGeneratorClient.hentKafkaKey(KafkaKeyGeneratorRequest("11111111111"))!!.key `should be equal to` 1000

        kafkaKeyGeneratorMockWebServer.takeRequest().also {
            it.method `should be equal to` "POST"
            it.path `should be equal to` "/api/v1/record-key"
            it.headers["Authorization"]!!.startsWith("Bearer") `should be equal to` true
        }
    }

    @Test
    fun `Kaster HttpClientErrorException npår ressurs ikke finne`() {
        kafkaKeyGeneratorMockWebServer.enqueue(MockResponse().setResponseCode(404))

        assertThrows<HttpClientErrorException> {
            kafkaKeyGeneratorClient.hentKafkaKey(KafkaKeyGeneratorRequest("11111111111"))
        }
    }

    @Test
    fun `Kaster HttpServerErrorException ved server feil`() {
        kafkaKeyGeneratorMockWebServer.enqueue(MockResponse().setResponseCode(500))

        assertThrows<HttpServerErrorException> {
            kafkaKeyGeneratorClient.hentKafkaKey(KafkaKeyGeneratorRequest("11111111111"))
        }
    }
}
