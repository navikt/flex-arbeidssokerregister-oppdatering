package no.nav.helse.flex

import no.nav.security.token.support.client.core.ClientProperties
import no.nav.security.token.support.client.core.oauth2.OAuth2AccessTokenService
import no.nav.security.token.support.client.spring.oauth2.EnableOAuth2Client
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpRequest
import org.springframework.http.client.ClientHttpRequestExecution
import org.springframework.http.client.ClientHttpRequestInterceptor
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory
import org.springframework.web.client.RestClient
import java.time.Duration

const val CLIENT_API_CONNECT_TIMEOUT = 5L
const val CLIENT_API_READ_TIMEOUT = 10L

@EnableOAuth2Client(cacheEnabled = true)
@Configuration
class RestClientConfiguration {
    @Bean
    fun restClientBuilder(): RestClient.Builder {
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
                setConnectTimeout(Duration.ofSeconds(CLIENT_API_CONNECT_TIMEOUT))
                setReadTimeout(Duration.ofSeconds(CLIENT_API_READ_TIMEOUT))
            }

        return RestClient
            .builder()
            .requestFactory(requestFactory)
    }

    @Bean
    fun restClient(restClientBuilder: RestClient.Builder): RestClient = restClientBuilder.build()
}

internal fun bearerTokenInterceptor(
    clientProperties: ClientProperties,
    oAuth2AccessTokenService: OAuth2AccessTokenService,
): ClientHttpRequestInterceptor =
    ClientHttpRequestInterceptor { request: HttpRequest, body: ByteArray, execution: ClientHttpRequestExecution ->
        val response = oAuth2AccessTokenService.getAccessToken(clientProperties)
        response.access_token?.let { request.headers.setBearerAuth(it) }
        execution.execute(request, body)
    }
