package no.nav.helse.flex.paw

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.helse.flex.objectMapper
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType.APPLICATION_JSON
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.time.OffsetDateTime

@Component
class ArbeidssokerregisterClient(
    @Value("\${ARBEIDSSOEKERREGISTERET_API_URL}")
    private val url: String,
    @Qualifier("arbeidssokerregisterRestClient")
    val restClient: RestClient,
) {
    fun hentSisteArbeidssokerperiode(request: ArbeidssokerperiodeRequest): List<ArbeidssokerperiodeResponse> =
        restClient
            .post()
            .uri("$url/api/v1/veileder/arbeidssoekerperioder?siste=true")
            .contentType(APPLICATION_JSON)
            .body(request)
            .retrieve()
            .body(String::class.java)!!
            .tilArbeidssokerperiodeResponse()

    private fun String.tilArbeidssokerperiodeResponse(): List<ArbeidssokerperiodeResponse> = objectMapper.readValue(this)
}

data class ArbeidssokerperiodeRequest(
    val identitetsnummer: String,
)

data class ArbeidssokerperiodeResponse(
    val periodeId: String,
    val startet: Metadata,
    val avsluttet: Metadata?,
)

data class Metadata(
    val tidspunkt: OffsetDateTime,
    val utfoertAv: Bruker,
    val kilde: String,
    val aarsak: String,
    val tidspunktFraKilde: TidspunktFraKilde?,
)

data class Bruker(
    val type: String,
    // UKJENT_VERDI, UDEFINERT, VEILEDER, SYSTEM, SLUTTBRUKER
    val id: String,
)

data class TidspunktFraKilde(
    val tidspunkt: OffsetDateTime,
    // UKJENT_VERDI, FORSINKELSE, RETTING, SLETTET, TIDSPUNKT_KORRIGERT
    val avviksType: String,
)
