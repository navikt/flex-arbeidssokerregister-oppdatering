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

    fun findByFnr(string: String): List<Arbeidssokerperiode>?
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
    val arbeidssokerperiodeId: String? = null,
    @Column("sendt_paavegneav")
    val sendtPaaVegneAv: Instant? = null,
    val avsluttetMottatt: Instant? = null,
    val avsluttetTidspunkt: Instant? = null,
    val sendtAvsluttet: Instant? = null,
    val avsluttetAarsak: AvsluttetAarsak? = null,
)

enum class AvsluttetAarsak {
    BRUKER,
    AVSLUTTET_PERIODE,
}
