package no.nav.helse.flex.sykepengesoknad

import no.nav.helse.flex.FNR
import no.nav.helse.flex.FellesTestOppsett
import no.nav.helse.flex.VEDTAKSPERIODE_ID
import no.nav.helse.flex.arbeidssokerperiode.Arbeidssokerperiode
import no.nav.helse.flex.arbeidssokerperiode.AvsluttetAarsak
import no.nav.helse.flex.lagSoknad
import no.nav.helse.flex.`should be within seconds of`
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadsstatusDTO
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadstypeDTO
import no.nav.helse.flex.sykepengesoknad.kafka.SykepengesoknadDTO
import no.nav.paw.bekreftelse.paavegneav.v1.vo.Stopp
import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.`should not be equal to`
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import java.time.LocalDate
import java.util.*
import kotlin.jvm.optionals.toList

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class PeriodebekreftelseIntegrationTest : FellesTestOppsett() {
    @AfterEach
    fun verifiserAtTopicErTomt() {
        bekreftelseConsumer.fetchRecords().size `should be equal to` 0
        slettFraDatabase()
    }

    private val arbeidssokerperiodeId = UUID.randomUUID().toString()
    private val opprettet = Instant.now()
    private val sendtPaaVegneAv = Instant.now()

    @Test
    fun `Søknad hvor bruker vil fortsett å være arbeidssøker`() {
        val lagretPeriode = lagreArbeidssokerperiode()

        val fortsattArbeidssoker = true
        val inntektUnderveis = false

        val soknad = lagSendtSoknad(fortsattArbeidssoker, inntektUnderveis)
        sykepengesoknadService.behandleSoknad(soknad)

        verifiserPeriodebekreftelse(lagretPeriode, soknad, fortsattArbeidssoker, inntektUnderveis)

        arbeidssokerperiodeRepository.findById(lagretPeriode.id!!).toList().single().also {
            it.sendtAvsluttet `should be equal to` null
        }

        verifiserKafkaMelding(soknad, fortsattArbeidssoker, inntektUnderveis)
    }

    @Test
    @Order(2)
    fun `Søknad hvor bruker ikke vil fortsett å være arbeidssøker`() {
        val lagretPeriode = lagreArbeidssokerperiode()

        val fortsattArbeidssoker = false
        val inntektUnderveis = false

        val soknad = lagSendtSoknad(fortsattArbeidssoker, inntektUnderveis)
        sykepengesoknadService.behandleSoknad(soknad)

        verifiserPeriodebekreftelse(lagretPeriode, soknad, fortsattArbeidssoker, inntektUnderveis)

        arbeidssokerperiodeRepository.findById(lagretPeriode.id!!).toList().single().also {
            it.sendtAvsluttet!! `should be within seconds of` (1 to Instant.now())
            it.avsluttetAarsak `should be equal to` AvsluttetAarsak.BRUKER
        }

        verifiserKafkaMelding(soknad, fortsattArbeidssoker, inntektUnderveis)
    }

    @Test
    fun `Søknad hvor bruker jobbet underveis`() {
        val lagretPeriode = lagreArbeidssokerperiode()

        val fortsattArbeidssoker = true
        val inntektUnderveis = true

        val soknad = lagSendtSoknad(fortsattArbeidssoker, inntektUnderveis)
        sykepengesoknadService.behandleSoknad(soknad)

        verifiserPeriodebekreftelse(lagretPeriode, soknad, fortsattArbeidssoker, inntektUnderveis)

        arbeidssokerperiodeRepository.findById(lagretPeriode.id!!).toList().single().also {
            it.sendtAvsluttet `should be equal to` null
        }

        verifiserKafkaMelding(soknad, fortsattArbeidssoker, inntektUnderveis)
    }

    @Test
    fun `Søknad som mangler vedtaksperiodId feiler`() {
        lagreArbeidssokerperiode()

        val soknad =
            lagSendtSoknad(fortsattArbeidssoker = true, inntektUnderveis = false).copy(friskTilArbeidVedtakId = null)

        assertThrows<Exception> {
            sykepengesoknadService.behandleSoknad(soknad)
        }

        periodebekreftelseRepository.findAll().toList().size `should be equal to` 0
    }

    @Test
    fun `Soknad som refererer ukjent arbeidssøkerperiode feiler`() {
        val soknad = lagSendtSoknad(fortsattArbeidssoker = true, inntektUnderveis = false)

        assertThrows<PeriodebekreftelseException> {
            sykepengesoknadService.behandleSoknad(soknad)
        }

        periodebekreftelseRepository.findAll().toList().size `should be equal to` 0
    }

    @Test
    fun `Søknad som ikke er siste søknad og mangler verdi for fortsattArbeidssoker feiler`() {
        lagreArbeidssokerperiode()

        val soknad =
            lagSendtSoknad(fortsattArbeidssoker = true, inntektUnderveis = false).copy(fortsattArbeidssoker = null)

        assertThrows<PeriodebekreftelseException> {
            sykepengesoknadService.behandleSoknad(soknad)
        }

        periodebekreftelseRepository.findAll().toList().size `should be equal to` 0
    }

    @Test
    fun `Søknad hvor bruker ikke har fått svart på inntektUnderveis`() {
        lagreArbeidssokerperiode()

        val soknad =
            lagSendtSoknad(fortsattArbeidssoker = false, inntektUnderveis = false).copy(inntektUnderveis = null)

        sykepengesoknadService.behandleSoknad(soknad)

        periodebekreftelseRepository.findAll().toList().single().also {
            it.inntektUnderveis `should be equal to` null
        }

        verifiserKafkaMelding(soknad, false, false)
    }

    @Test
    fun `Søknad med feil status blir ikke behandlet`() {
        lagreArbeidssokerperiode()

        val soknad =
            lagSendtSoknad(fortsattArbeidssoker = true, inntektUnderveis = false).copy(status = SoknadsstatusDTO.NY)
        sykepengesoknadService.behandleSoknad(soknad)

        periodebekreftelseRepository.findAll().toList().size `should be equal to` 0
    }

    @Test
    fun `Søknad som korrigerer blir ikke behandlet`() {
        val lagretPeriode = lagreArbeidssokerperiode()

        val fortsattArbeidssoker = false
        val inntektUnderveis = false

        val soknad = lagSendtSoknad(fortsattArbeidssoker, inntektUnderveis)
        sykepengesoknadService.behandleSoknad(soknad)

        val korrigerendeSoknad = lagSendtSoknad(fortsattArbeidssoker, inntektUnderveis).copy(korrigerer = soknad.id)
        sykepengesoknadService.behandleSoknad(korrigerendeSoknad)

        verifiserPeriodebekreftelse(lagretPeriode, soknad, fortsattArbeidssoker, inntektUnderveis)

        verifiserKafkaMelding(soknad, fortsattArbeidssoker, inntektUnderveis)
    }

    @Test
    fun `Søknad er siste i arbeidssøkerperioden`() {
        val lagretPeriode = lagreArbeidssokerperiode()

        val soknad = lagSisteSoknadForArbeidsokerperiode(lagretPeriode)
        sykepengesoknadService.behandleSoknad(soknad)

        verifiserPeriodebekreftelse(arbeidssokerperiode = lagretPeriode, soknad = soknad, avsluttendeSoknad = true)

        arbeidssokerperiodeRepository.findById(lagretPeriode.id!!).toList().single().also {
            it.sendtAvsluttet!! `should be within seconds of` (1 to Instant.now())
            it.avsluttetAarsak `should be equal to` AvsluttetAarsak.AVSLUTTET_PERIODE
        }

        paaVegneAvConsumer.waitForRecords(1).single().also {
            it.key() `should be equal to` lagretPeriode.kafkaRecordKey
            it.value().also {
                it.periodeId.toString() `should be equal to` lagretPeriode.arbeidssokerperiodeId
                (it.handling as Stopp) `should not be equal to` null
            }
        }
    }

    @Test
    fun `Søknad med feil type blir ikke behandlet`() {
        lagreArbeidssokerperiode()

        val soknad =
            lagSendtSoknad(
                inntektUnderveis = false,
                fortsattArbeidssoker = true,
            ).copy(type = SoknadstypeDTO.ARBEIDSTAKERE)
        sykepengesoknadService.behandleSoknad(soknad)

        periodebekreftelseRepository.findAll().toList().size `should be equal to` 0
    }

    @Test
    fun `Duplikat søknad blir ikke behandlet`() {
        val lagretPeriode = lagreArbeidssokerperiode()

        val fortsattArbeidssoker = true
        val inntektUnderveis = false

        val soknad = lagSendtSoknad(fortsattArbeidssoker, inntektUnderveis)
        sykepengesoknadService.behandleSoknad(soknad)

        // Simulerer dobbel innsending av samme søknad.
        sykepengesoknadService.behandleSoknad(soknad)

        verifiserPeriodebekreftelse(lagretPeriode, soknad, fortsattArbeidssoker, inntektUnderveis)

        arbeidssokerperiodeRepository.findById(lagretPeriode.id!!).toList().single().also {
            it.sendtAvsluttet `should be equal to` null
        }

        verifiserKafkaMelding(soknad, fortsattArbeidssoker, inntektUnderveis)
    }

    private fun lagreArbeidssokerperiode(
        fom: LocalDate = LocalDate.of(2025, 1, 1),
        tom: LocalDate = LocalDate.of(2025, 1, 31),
    ): Arbeidssokerperiode =
        arbeidssokerperiodeRepository.save(
            Arbeidssokerperiode(
                fnr = FNR,
                vedtaksperiodeId = VEDTAKSPERIODE_ID,
                vedtaksperiodeFom = fom,
                vedtaksperiodeTom = tom,
                opprettet = opprettet,
                kafkaRecordKey = -3771L,
                arbeidssokerperiodeId = arbeidssokerperiodeId,
                sendtPaaVegneAv = sendtPaaVegneAv,
            ),
        )

    private fun lagSendtSoknad(
        fortsattArbeidssoker: Boolean,
        inntektUnderveis: Boolean,
    ): SykepengesoknadDTO =
        lagSoknad(
            status = SoknadsstatusDTO.SENDT,
            fortsattArbeidssoker = fortsattArbeidssoker,
            inntektUnderveis = inntektUnderveis,
        )

    private fun lagSisteSoknadForArbeidsokerperiode(arbeidssokerperiode: Arbeidssokerperiode): SykepengesoknadDTO =
        lagSoknad(
            status = SoknadsstatusDTO.SENDT,
            soknadFomTom =
                Periode(
                    arbeidssokerperiode.vedtaksperiodeTom.minusDays(13),
                    arbeidssokerperiode.vedtaksperiodeTom,
                ),
            periodeFomTom = Periode(arbeidssokerperiode.vedtaksperiodeFom, arbeidssokerperiode.vedtaksperiodeTom),
        )

    private fun verifiserPeriodebekreftelse(
        arbeidssokerperiode: Arbeidssokerperiode,
        soknad: SykepengesoknadDTO,
        fortsattArbeidssoker: Boolean? = null,
        inntektUnderveis: Boolean? = null,
        avsluttendeSoknad: Boolean? = false,
    ) {
        periodebekreftelseRepository.findAll().toList().single().also {
            it.id `should not be equal to` null
            it.arbeidssokerperiodeId `should be equal to` arbeidssokerperiode.id
            it.sykepengesoknadId `should be equal to` soknad.id
            it.fortsattArbeidssoker `should be equal to` fortsattArbeidssoker
            it.inntektUnderveis `should be equal to` inntektUnderveis
            it.opprettet `should be within seconds of` (1 to Instant.now())
            it.avsluttendeSoknad `should be equal to` avsluttendeSoknad
        }
    }

    private fun verifiserKafkaMelding(
        soknad: SykepengesoknadDTO,
        fortsattArbeidssoker: Boolean,
        inntektUnderveis: Boolean,
    ) {
        bekreftelseConsumer.waitForRecords(1).single().also {
            it.key() `should be equal to` -3771L
            it.value().also {
                it.periodeId.toString() `should be equal to` arbeidssokerperiodeId
                it.bekreftelsesloesning.toString() `should be equal to` "FRISKMELDT_TIL_ARBEIDSFORMIDLING"
                it.svar.also {
                    it.sendtInnAv.also {
                        it.utfoertAv.type.toString() `should be equal to` "SLUTTBRUKER"
                        it.utfoertAv.id `should be equal to` FNR
                    }
                    it.gjelderFra `should be equal to` soknad.fom!!.toInstantAtStartOfDay()
                    it.gjelderTil `should be equal to` soknad.tom!!.plusDays(1).toInstantAtStartOfDay()
                    it.vilFortsetteSomArbeidssoeker `should be equal to` fortsattArbeidssoker
                    it.harJobbetIDennePerioden `should be equal to` inntektUnderveis
                }
            }
        }
    }
}
