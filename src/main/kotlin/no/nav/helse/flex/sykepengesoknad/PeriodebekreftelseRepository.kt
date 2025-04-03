package no.nav.helse.flex.sykepengesoknad

import org.springframework.data.annotation.Id
import org.springframework.data.jdbc.repository.query.Modifying
import org.springframework.data.jdbc.repository.query.Query
import org.springframework.data.relational.core.mapping.Table
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
interface PeriodebekreftelseRepository : CrudRepository<Periodebekreftelse, String> {
    fun findBySykepengesoknadId(string: String): Periodebekreftelse?

    @Modifying
    @Query("DELETE FROM periodebekreftelse WHERE arbeidssokerperiode_id = :string")
    fun deleteByArbeidssokerperiodeId(string: String): Long

    fun findByArbeidssokerperiodeId(string: String): List<Periodebekreftelse>
}

@Table("periodebekreftelse")
data class Periodebekreftelse(
    @Id
    val id: String? = null,
    val arbeidssokerperiodeId: String,
    val sykepengesoknadId: String,
    val fortsattArbeidssoker: Boolean?,
    val inntektUnderveis: Boolean?,
    val opprettet: Instant,
    val avsluttendeSoknad: Boolean = false,
)
