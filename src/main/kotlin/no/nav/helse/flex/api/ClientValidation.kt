package no.nav.helse.flex.api

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.helse.flex.api.OIDCIssuer.AZUREATOR
import no.nav.helse.flex.objectMapper
import no.nav.security.token.support.core.context.TokenValidationContextHolder
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class ClientValidation(
    private val tokenValidationContextHolder: TokenValidationContextHolder,
    @Value("\${AZURE_APP_PRE_AUTHORIZED_APPS}") private val preauthorizedApps: String,
) {
    private val allowedClients: List<PreAuthorizedClient> = objectMapper.readValue(preauthorizedApps)

    fun validateClientId(app: NamespaceAndApp) = validateClientId(listOf(app))

    private fun validateClientId(apps: List<NamespaceAndApp>) {
        val clientIds =
            allowedClients
                .filter { apps.contains(it.tilNamespaceAndApp()) }
                .map { it.clientId }

        val azpClaim = tokenValidationContextHolder.hentAzpClaim()
        if (clientIds.ikkeInneholder(azpClaim)) {
            throw UkjentClientException("Ukjent clientId: $azpClaim.")
        }
    }

    private fun TokenValidationContextHolder.hentAzpClaim(): String =
        this
            .getTokenValidationContext()
            .getJwtToken(AZUREATOR)
            ?.jwtTokenClaims
            ?.getStringClaim("azp")
            ?: throw UkjentClientException("Fant ikke azp claim!")

    private fun List<String>.ikkeInneholder(s: String): Boolean = !this.contains(s)

    private fun PreAuthorizedClient.tilNamespaceAndApp(): NamespaceAndApp {
        val splitt = name.split(":")
        return NamespaceAndApp(namespace = splitt[1], app = splitt[2])
    }

    private data class PreAuthorizedClient(
        val name: String,
        val clientId: String,
    )
}

data class NamespaceAndApp(
    val namespace: String,
    val app: String,
)

class UkjentClientException(
    message: String,
) : RuntimeException(message)
