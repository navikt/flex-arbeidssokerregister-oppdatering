package no.nav.helse.flex

import no.nav.security.token.support.client.core.ClientProperties
import no.nav.security.token.support.client.core.oauth2.OAuth2AccessTokenService
import no.nav.security.token.support.client.spring.ClientConfigurationProperties
import no.nav.security.token.support.client.spring.oauth2.EnableOAuth2Client
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpRequest
import org.springframework.http.client.ClientHttpRequestExecution
import org.springframework.http.client.ClientHttpRequestInterceptor
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory
import org.springframework.web.client.RestClient
import java.time.Duration

const val REST_CLIENT_CONNECT_TIMEOUT = 5L
const val REST_CLIENT_API_READ_TIMEOUT = 10L

@EnableOAuth2Client(cacheEnabled = true)
@Configuration
class RestClientConfig {
    @Bean
    fun kafkaKeyGeneratorRestClient(
        @Value("\${KAFKA_KEY_GENERATOR_URL}")
        url: String,
        oAuth2AccessTokenService: OAuth2AccessTokenService,
        clientConfigurationProperties: ClientConfigurationProperties,
    ): RestClient =
        lagRestClientBuilder()
            .baseUrl(url)
            .requestInterceptor(
                lagBearerTokenInterceptor(
                    clientConfigurationProperties.registration["kafka-key-generator-client-credentials"]!!,
                    oAuth2AccessTokenService,
                ),
            ).build()

    @Bean
    fun arbeidssokerregisterRestClient(
        @Value("\${ARBEIDSSOEKERREGISTERET_API_URL}")
        url: String,
        oAuth2AccessTokenService: OAuth2AccessTokenService,
        clientConfigurationProperties: ClientConfigurationProperties,
    ): RestClient =
        lagRestClientBuilder()
            .baseUrl(url)
            .requestInterceptor(
                lagBearerTokenInterceptor(
                    clientConfigurationProperties.registration["arbeidssoekerregisteret-client-credentials"]!!,
                    oAuth2AccessTokenService,
                ),
            ).build()

    private fun lagRestClientBuilder(
        connectTimeout: Long = REST_CLIENT_CONNECT_TIMEOUT,
        readTimeout: Long = REST_CLIENT_API_READ_TIMEOUT,
    ): RestClient.Builder {
        val connectionManager =
            PoolingHttpClientConnectionManager().apply {
                maxTotal = 10
                defaultMaxPerRoute = 10
            }

        val httpClient =
            HttpClientBuilder
                .create()
                .setConnectionManager(connectionManager)
                .build()

        val requestFactory =
            HttpComponentsClientHttpRequestFactory(httpClient).apply {
                setConnectTimeout(Duration.ofSeconds(connectTimeout))
                setReadTimeout(Duration.ofSeconds(readTimeout))
            }

        return RestClient
            .builder()
            .requestFactory(requestFactory)
    }

    private fun lagBearerTokenInterceptor(
        clientProperties: ClientProperties,
        oAuth2AccessTokenService: OAuth2AccessTokenService,
    ): ClientHttpRequestInterceptor =
        ClientHttpRequestInterceptor { request: HttpRequest, body: ByteArray, execution: ClientHttpRequestExecution ->
            val response = oAuth2AccessTokenService.getAccessToken(clientProperties)
            response.access_token?.let { request.headers.setBearerAuth(it) }
            execution.execute(request, body)
        }
}
