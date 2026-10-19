package no.nav.helse.flex.arbeidssokerperiode

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository
import java.time.Instant
import java.time.LocalDate

@Repository
interface ArbeidssokerperiodeRepository : CrudRepository<Arbeidssokerperiode, String> {
    fun findByVedtaksperiodeId(vedtaksperiodeId: String): Arbeidssokerperiode?

    fun findByArbeidssokerperiodeId(id: String): List<Arbeidssokerperiode>

    fun findByFnr(fnr: String): List<Arbeidssokerperiode>
}

@Table("arbeidssokerperiode")
data class Arbeidssokerperiode(
    @Id
    val id: String? = null,
    val fnr: String,
    val vedtaksperiodeId: String,
    val vedtaksperiodeFom: LocalDate,
    val vedtaksperiodeTom: LocalDate,
    val opprettet: Instant,
    val kafkaRecordKey: Long? = null,
    // Id på arbeidssøkerperioden i Arbeidssøkerregisteret.
    val arbeidssokerperiodeId: String? = null,
    // Når PaaVegneAvStart-melding ble sendt til Arbeidssøkerregisteret. Settes når arbeidssøkerperioden opprettes og
    // oppdateres hvis perioden restartes.
    @Column("sendt_paavegneav")
    val sendtPaaVegneAv: Instant? = null,
    // Når avsluttet periode fra Arbeidssøkerregisteret ble mottatt. Nullstilles når arbeidssøkerperioden restartes.
    val avsluttetMottatt: Instant? = null,
    // Når perioden ble avsluttet i Arbeidssøkerregisteret. Nullstilles hvis arbeidssøkerperioden restartes.
    val avsluttetTidspunkt: Instant? = null,
    // Når PaaVegneAvStopp-melding ble sendt til Arbeidssøkerregisteret. Lagres samtidig med avsluttetAarsak.
    val sendtAvsluttet: Instant? = null,
    // Hvorfor PaaVegneAvStopp-melding ble sendt. Lagres samtidig med sendtAvsluttet.
    val avsluttetAarsak: AvsluttetAarsak? = null,
)

enum class AvsluttetAarsak {
    // Bruker svarte i søknaden at hen ikke vil fortsette å være registert som arbeidssøker.
    BRUKER,

    // Søknaden var siste søknad i vedtaksperioden.
    AVSLUTTET_PERIODE,

    // En søknad for bruker fikk status UTGAATT.
    UTGAATT,
}
