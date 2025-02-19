package no.nav.helse.flex.arbeidssokerregister

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType.APPLICATION_JSON
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

@Component
class KafkaKeyGeneratorClient(
    @Value("\${KAFKA_KEY_GENERATOR_URL}")
    private val url: String,
    @Qualifier("kafkaKeyGeneratorRestClient")
    val restClient: RestClient,
) {
    fun hentKafkaKey(request: KafkaKeyGeneratorRequest) =
        restClient
            .post()
            .uri("$url/api/v1/record-key")
            .contentType(APPLICATION_JSON)
            .body(request)
            .retrieve()
            .body(KafkaKeyGeneratorResponse::class.java)
}

data class KafkaKeyGeneratorRequest(
    val ident: String,
)

data class KafkaKeyGeneratorResponse(
    val key: Int,
)
