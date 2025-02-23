package no.nav.helse.flex

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime

@Repository
interface ArbeidssokerperiodeRepository : CrudRepository<Arbeidssokerperiode, String> {
    fun findByVedtaksperiodeId(vedtaksperiodeId: String): Arbeidssokerperiode?
}

@Table("arbeidssokerperiode")
data class Arbeidssokerperiode(
    @Id
    val id: String? = null,
    val fnr: String,
    val vedtaksperiodeId: String,
    val opprettet: OffsetDateTime,
)
