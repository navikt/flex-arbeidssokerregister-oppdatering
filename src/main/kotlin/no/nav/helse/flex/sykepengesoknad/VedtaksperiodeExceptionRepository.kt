package no.nav.helse.flex.sykepengesoknad

import org.springframework.data.annotation.Id
import org.springframework.data.jdbc.repository.query.Modifying
import org.springframework.data.jdbc.repository.query.Query
import org.springframework.data.relational.core.mapping.Table
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
interface VedtaksperiodeExceptionRepository : CrudRepository<VedtaksperiodeException, String> {
    @Modifying
    @Query("DELETE FROM vedtaksperiode_exception WHERE fnr = :fnr")
    fun deleteByFnr(fnr: String)

    fun findBySykepengesoknadId(sykepengesoknadId: String): VedtaksperiodeException?
}

@Table("vedtaksperiode_exception")
data class VedtaksperiodeException(
    @Id
    val id: String? = null,
    val opprettet: Instant,
    val vedtaksperiodeId: String,
    val sykepengesoknadId: String,
    val fnr: String,
    val exceptionClassName: String,
    val exceptionMessage: String? = null,
    val behandlet: Instant? = null,
)
