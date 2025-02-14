package no.nav.helse.flex.paw

import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType.APPLICATION_JSON
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.time.OffsetDateTime

@Component
class ArbeidssoekerregisteretClient(
    @Value("\${ARBEIDSSOEKERREGISTERET_API_URL}")
    private val url: String,
    private val arbeidssoekerregisteretRestClient: RestClient,
) {
    fun hentSisteArbeidssokerperiode(request: ArbeidssokerperiodeRequest) =
        arbeidssoekerregisteretRestClient
            .post()
            .uri("$url/api/v1/veileder/arbeidssoekerperioder?siste=true")
            .contentType(APPLICATION_JSON)
            .body(request)
            .retrieve()
            .body(String::class.java)
}

data class ArbeidssokerperiodeRequest(
    val identitetsnummer: String,
)

data class ArbeidssokerperiodeResponse(
    val periodeId: String,
    val startet: Metadata,
    val avsluttet: Metadata,
)

data class Metadata(
    val tidspunkt: OffsetDateTime,
    val utfoertAv: Bruker,
    val kilde: String,
    val aarsak: String,
    val tidspunktFraKilde: TidspunktFraKilde,
)

data class Bruker(
    val type: String,
    val id: String,
)

enum class BrukerType {
    UKJENT_VERDI,
    UDEFINERT,
    VEILEDER,
    SYSTEM,
    SLUTTBRUKER,
}

data class TidspunktFraKilde(
    val tidspunkt: OffsetDateTime,
    val avviksType: String,
)

enum class AvviksType {
    UKJENT_VERDI,
    FORSINKELSE,
    RETTING,
    SLETTET,
    TIDSPUNKT_KORRIGERT,
}
