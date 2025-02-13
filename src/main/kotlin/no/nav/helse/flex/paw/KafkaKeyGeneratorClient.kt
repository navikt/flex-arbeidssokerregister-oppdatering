package no.nav.helse.flex.paw

import no.nav.helse.flex.bearerTokenInterceptor
import no.nav.security.token.support.client.core.oauth2.OAuth2AccessTokenService
import no.nav.security.token.support.client.spring.ClientConfigurationProperties
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType.APPLICATION_JSON
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

@Component
class KafkaKeyGeneratorClient(
    @Value("\${KAFKA_KEY_GENERATOR_URL}")
    private val url: String,
    restClientBuilder: RestClient.Builder,
    oAuth2AccessTokenService: OAuth2AccessTokenService,
    clientConfigurationProperties: ClientConfigurationProperties,
) {
    private val restClient =
        restClientBuilder
            .baseUrl(url)
            .requestInterceptor(
                bearerTokenInterceptor(
                    clientConfigurationProperties.registration["kafka-key-generator-client-credentials"]!!,
                    oAuth2AccessTokenService,
                ),
            ).build()

    fun hentKafkaKey(request: KafkaKeyGeneratorRequest) =
        restClient
            .post()
            .uri { uriBuilder ->
                uriBuilder
                    .path("/api/v1/record-key")
                    .build()
            }.contentType(APPLICATION_JSON)
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
