package no.nav.helse.flex

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadsstatusDTO
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadstypeDTO
import no.nav.helse.flex.sykepengesoknad.kafka.SykepengesoknadDTO
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.OffsetDateTime

@Service
class ArbeidssokerperiodeService(
    private val arbeidssokerperiodeRepository: ArbeidssokerperiodeRepository,
) {
    private val log = logger()

    @Transactional
    fun behandleSoknad(sykepengesoknadDTO: SykepengesoknadDTO) {
        if (sykepengesoknadDTO.skalBehandles()) {
            behandleVedtaksperiode(sykepengesoknadDTO.tilVedtaksperiode())
        }
    }

    private fun behandleVedtaksperiode(vedtaksperiode: FriskTilArbeidVedtaksperiode) {
        if (erNyVedtaksperiode(vedtaksperiode)) {
            arbeidssokerperiodeRepository.save(vedtaksperiode.toArbeidssokerperiode())
            log.info("Lagret vedtaksperiode med id ${vedtaksperiode.vedtaksperiodeId}.")
        }
    }

    private fun erNyVedtaksperiode(vedtaksperiode: FriskTilArbeidVedtaksperiode) =
        arbeidssokerperiodeRepository.findByVedtaksperiodeId(vedtaksperiode.vedtaksperiodeId) == null

    private fun SykepengesoknadDTO.skalBehandles() =
        type == SoknadstypeDTO.FRISKMELDT_TIL_ARBEIDSFORMIDLING && status == SoknadsstatusDTO.FREMTIDIG

    fun FriskTilArbeidVedtaksperiode.toArbeidssokerperiode() =
        Arbeidssokerperiode(
            fnr = this.fnr,
            vedtaksperiodeId = this.vedtaksperiodeId,
            opprettet = OffsetDateTime.now(),
        )

    fun SykepengesoknadDTO.tilVedtaksperiode(): FriskTilArbeidVedtaksperiode {
        val periode = this.friskTilArbeidVedtakPeriode!!.tilPeriode()
        return FriskTilArbeidVedtaksperiode(
            fnr = this.fnr,
            vedtaksperiodeId = this.friskTilArbeidVedtakId!!,
            fom = periode.fom,
            tom = periode.tom,
        )
    }
}

fun String.tilPeriode(): Periode = objectMapper.readValue(this)

data class FriskTilArbeidVedtaksperiode(
    val fnr: String,
    val vedtaksperiodeId: String,
    val fom: LocalDate,
    val tom: LocalDate,
)

data class Periode(
    val fom: LocalDate,
    val tom: LocalDate,
)
