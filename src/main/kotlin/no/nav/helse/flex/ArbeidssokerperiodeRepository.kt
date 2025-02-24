package no.nav.helse.flex

import org.springframework.data.annotation.Id
import org.springframework.data.jdbc.repository.query.Modifying
import org.springframework.data.jdbc.repository.query.Query
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime

@Repository
interface ArbeidssokerperiodeRepository : CrudRepository<Arbeidssokerperiode, String> {
    fun findByVedtaksperiodeId(vedtaksperiodeId: String): Arbeidssokerperiode?

    @Modifying
    @Query("DELETE FROM arbeidssokerperiode WHERE fnr = :fnr")
    fun deleteByFnr(fnr: String): Long
}

@Table("arbeidssokerperiode")
data class Arbeidssokerperiode(
    @Id
    val id: String? = null,
    val fnr: String,
    val vedtaksperiodeId: String,
    val opprettet: OffsetDateTime,
    val kafkaRecordKey: Long? = null,
    val arbeidssokerperiodeId: String? = null,
    @Column("sendt_paavegneav")
    val sendtPaaVegneAv: OffsetDateTime? = null,
)
