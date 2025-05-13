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
import org.amshove.kluent.shouldHaveSize
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
    private val soknadFomTom: Periode = Periode(fom = LocalDate.of(2025, 1, 1), tom = LocalDate.of(2025, 1, 14))
    private val periodeFomTom: Periode = Periode(fom = LocalDate.of(2025, 1, 1), tom = LocalDate.of(2025, 1, 31))

    @Test
    @Order(1)
    fun `Søknad med med ignorerArbeidssokerregister satt blir ikke behandlet`() {
        val fortsattArbeidssoker = true
        val inntektUnderveis = false

        val soknad = lagSendtSoknad(fortsattArbeidssoker, inntektUnderveis).copy(ignorerArbeidssokerregister = true)
        sykepengesoknadService.behandleSoknad(soknad)

        periodebekreftelseRepository.findAll().toList() shouldHaveSize 0
    }

    @Test
    @Order(2)
    fun `Søknad hvor bruker vil fortsett å være arbeidssøker`() {
        val lagretPeriode = lagreArbeidssokerperiode()

        val fortsattArbeidssoker = true
        val inntektUnderveis = false

        val soknad = lagSendtSoknad(fortsattArbeidssoker, inntektUnderveis)
        sykepengesoknadService.behandleSoknad(soknad)

        verifiserLagretPeriodebekreftelse(lagretPeriode, soknad, fortsattArbeidssoker, inntektUnderveis)

        arbeidssokerperiodeRepository.findById(lagretPeriode.id!!).toList().single().also {
            it.sendtAvsluttet `should be equal to` null
        }

        verifiserBekreftelseKafkaMelding(soknad, inntektUnderveis)
    }

    @Test
    @Order(2)
    fun `Søknad hvor bruker ikke vil fortsett å være arbeidssøker`() {
        val lagretPeriode = lagreArbeidssokerperiode()

        val fortsattArbeidssoker = false
        val inntektUnderveis = false

        val soknad = lagSendtSoknad(fortsattArbeidssoker, inntektUnderveis)
        sykepengesoknadService.behandleSoknad(soknad)

        verifiserLagretPeriodebekreftelse(lagretPeriode, soknad, fortsattArbeidssoker, inntektUnderveis)

        arbeidssokerperiodeRepository.findById(lagretPeriode.id!!).toList().single().also {
            it.sendtAvsluttet!! `should be within seconds of` (1 to Instant.now())
            it.avsluttetAarsak `should be equal to` AvsluttetAarsak.BRUKER
        }

        paaVegneAvConsumer.waitForRecords(1).single().also { consumerRecord ->
            consumerRecord.key() `should be equal to` lagretPeriode.kafkaRecordKey
            consumerRecord.value().also {
                it.periodeId.toString() `should be equal to` lagretPeriode.arbeidssokerperiodeId
                (it.handling as Stopp) `should not be equal to` null
            }
        }
    }

    @Test
    fun `Søknad hvor bruker jobbet underveis`() {
        val lagretPeriode = lagreArbeidssokerperiode()

        val fortsattArbeidssoker = true
        val inntektUnderveis = true

        val soknad = lagSendtSoknad(fortsattArbeidssoker, inntektUnderveis)
        sykepengesoknadService.behandleSoknad(soknad)

        verifiserLagretPeriodebekreftelse(lagretPeriode, soknad, fortsattArbeidssoker, inntektUnderveis)

        arbeidssokerperiodeRepository.findById(lagretPeriode.id!!).toList().single().also {
            it.sendtAvsluttet `should be equal to` null
        }

        verifiserBekreftelseKafkaMelding(soknad, inntektUnderveis)
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
            lagSendtSoknad(fortsattArbeidssoker = true, inntektUnderveis = false).copy(inntektUnderveis = null)

        sykepengesoknadService.behandleSoknad(soknad)

        periodebekreftelseRepository.findAll().toList().single().also {
            it.inntektUnderveis `should be equal to` null
        }

        verifiserBekreftelseKafkaMelding(soknad = soknad, inntektUnderveis = false)
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

        val fortsattArbeidssoker = true
        val inntektUnderveis = false

        val soknad = lagSendtSoknad(fortsattArbeidssoker, inntektUnderveis)
        sykepengesoknadService.behandleSoknad(soknad)

        val korrigerendeSoknad = lagSendtSoknad(fortsattArbeidssoker, inntektUnderveis).copy(korrigerer = soknad.id)
        sykepengesoknadService.behandleSoknad(korrigerendeSoknad)

        verifiserLagretPeriodebekreftelse(lagretPeriode, soknad, fortsattArbeidssoker, inntektUnderveis)

        verifiserBekreftelseKafkaMelding(soknad, inntektUnderveis)
    }

    @Test
    fun `Søknad er siste i arbeidssøkerperioden`() {
        val lagretPeriode = lagreArbeidssokerperiode()

        val soknad = lagSisteSoknadForArbeidsokerperiode(lagretPeriode)
        sykepengesoknadService.behandleSoknad(soknad)

        verifiserLagretPeriodebekreftelse(
            arbeidssokerperiode = lagretPeriode,
            soknad = soknad,
            avsluttendeSoknad = true,
        )

        arbeidssokerperiodeRepository.findById(lagretPeriode.id!!).toList().single().also {
            it.sendtAvsluttet!! `should be within seconds of` (1 to Instant.now())
            it.avsluttetAarsak `should be equal to` AvsluttetAarsak.AVSLUTTET_PERIODE
        }

        paaVegneAvConsumer.waitForRecords(1).single().also { consumerRecord ->
            consumerRecord.key() `should be equal to` lagretPeriode.kafkaRecordKey
            consumerRecord.value().also {
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

        verifiserLagretPeriodebekreftelse(lagretPeriode, soknad, fortsattArbeidssoker, inntektUnderveis)

        arbeidssokerperiodeRepository.findById(lagretPeriode.id!!).toList().single().also {
            it.sendtAvsluttet `should be equal to` null
        }

        verifiserBekreftelseKafkaMelding(soknad, inntektUnderveis)
    }

    @Test
    fun konverterOsloDatoTilInstantSommertid() {
        LocalDate.of(2025, 5, 10).toInstantAtStartOfDayOsloTid().toString() `should be equal to` "2025-05-09T22:00:00Z"
    }

    @Test
    fun konverterOsloDatoTilInstantVintertid() {
        LocalDate.of(2025, 1, 10).toInstantAtStartOfDayOsloTid().toString() `should be equal to` "2025-01-09T23:00:00Z"
    }

    private fun lagreArbeidssokerperiode(): Arbeidssokerperiode =
        arbeidssokerperiodeRepository.save(
            Arbeidssokerperiode(
                fnr = FNR,
                vedtaksperiodeId = VEDTAKSPERIODE_ID,
                vedtaksperiodeFom = periodeFomTom.fom,
                vedtaksperiodeTom = periodeFomTom.tom,
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
            soknadFomTom = soknadFomTom,
            periodeFomTom = periodeFomTom,
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

    private fun verifiserLagretPeriodebekreftelse(
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

    private fun verifiserBekreftelseKafkaMelding(
        soknad: SykepengesoknadDTO,
        inntektUnderveis: Boolean,
    ) {
        bekreftelseConsumer.waitForRecords(1).single().also { consumerRecord ->
            consumerRecord.key() `should be equal to` -3771L
            consumerRecord.value().also { bekreftelse ->
                bekreftelse.periodeId.toString() `should be equal to` arbeidssokerperiodeId
                bekreftelse.bekreftelsesloesning.toString() `should be equal to` "FRISKMELDT_TIL_ARBEIDSFORMIDLING"
                bekreftelse.svar.also { svar ->
                    svar.sendtInnAv.also { metadata ->
                        metadata.utfoertAv.type.toString() `should be equal to` "SLUTTBRUKER"
                        metadata.utfoertAv.id `should be equal to` FNR
                    }
                    svar.gjelderFra `should be equal to` soknad.fom!!.toInstantAtStartOfDayOsloTid()
                    svar.gjelderTil `should be equal to` soknad.tom!!.plusDays(1).toInstantAtStartOfDayOsloTid()
                    // Vi sender Bekreftelser kun når bruker vil fortsette å være arbeidssøker.
                    svar.vilFortsetteSomArbeidssoeker `should be equal to` true
                    svar.harJobbetIDennePerioden `should be equal to` inntektUnderveis
                }
            }
        }
    }
}
