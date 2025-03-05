package no.nav.helse.flex

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class EnvironmentToggles(
    @Value("\${nais.cluster}")
    private val naisClusterName: String,
) {
    fun erProduksjon(): Boolean = naisClusterName == "prod-gcp"

    fun naisClusterName(): String = naisClusterName
}
