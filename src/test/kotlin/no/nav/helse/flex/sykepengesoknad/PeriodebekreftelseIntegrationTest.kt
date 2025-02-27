package no.nav.helse.flex.sykepengesoknad

import no.nav.helse.flex.Arbeidssokerperiode
import no.nav.helse.flex.FNR
import no.nav.helse.flex.FellesTestOppsett
import no.nav.helse.flex.VEDTAKSPERIODE_ID
import no.nav.helse.flex.lagSoknad
import no.nav.helse.flex.`should be within seconds of`
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadsstatusDTO
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadstypeDTO
import no.nav.helse.flex.sykepengesoknad.kafka.SykepengesoknadDTO
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
import java.util.UUID

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
    fun `Søknad bekrefter at bruker fortsatt vil være arbeidssøker`() {
        val lagretPeriode = lagreArbeidssokerperiode()

        val fortsattArbeidssoker = true
        val inntektUnderveis = false

        val soknad = lagSendtSoknad(fortsattArbeidssoker, inntektUnderveis)
        sykepengesoknadService.behandleSoknad(soknad)

        verifiserPeriodebekreftelse(lagretPeriode, soknad, fortsattArbeidssoker, inntektUnderveis)

        verifiserKafkaMelding(soknad, fortsattArbeidssoker, inntektUnderveis)
    }

    @Test
    fun `Søknad viser at bruker jobbet underveis`() {
        val lagretPeriode = lagreArbeidssokerperiode()

        val fortsattArbeidssoker = true
        val inntektUnderveis = true

        val soknad = lagSendtSoknad(fortsattArbeidssoker, inntektUnderveis)
        sykepengesoknadService.behandleSoknad(soknad)

        verifiserPeriodebekreftelse(lagretPeriode, soknad, fortsattArbeidssoker, inntektUnderveis)

        verifiserKafkaMelding(soknad, fortsattArbeidssoker, inntektUnderveis)
    }

    @Test
    @Order(2)
    fun `Søknad bekrefter at bruker ikke vil fortsette å være`() {
        val lagretPeriode = lagreArbeidssokerperiode()

        val fortsattArbeidssoker = false
        val inntektUnderveis = false

        val soknad = lagSendtSoknad(fortsattArbeidssoker, inntektUnderveis)
        sykepengesoknadService.behandleSoknad(soknad)

        verifiserPeriodebekreftelse(lagretPeriode, soknad, fortsattArbeidssoker, inntektUnderveis)

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
    fun `Søknad som ikke har verdi for fortsattArbeidssoker feiler`() {
        lagreArbeidssokerperiode()

        val soknad =
            lagSendtSoknad(fortsattArbeidssoker = true, inntektUnderveis = false).copy(fortsattArbeidssoker = null)

        assertThrows<Exception> {
            sykepengesoknadService.behandleSoknad(soknad)
        }

        periodebekreftelseRepository.findAll().toList().size `should be equal to` 0
    }

    @Test
    fun `Søknad som ikke har verdi for inntektUnderveis feiler`() {
        lagreArbeidssokerperiode()

        val soknad =
            lagSendtSoknad(fortsattArbeidssoker = true, inntektUnderveis = false).copy(inntektUnderveis = null)

        assertThrows<Exception> {
            sykepengesoknadService.behandleSoknad(soknad)
        }

        periodebekreftelseRepository.findAll().toList().size `should be equal to` 0
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
    fun `Korrigerende søknad blir ikke behandlet`() {
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
    fun `Soknad med feil type blir ikke behandlet`() {
        lagreArbeidssokerperiode()

        val soknad =
            lagSendtSoknad(
                fortsattArbeidssoker = true,
                inntektUnderveis = false,
            ).copy(type = SoknadstypeDTO.ARBEIDSTAKERE)
        sykepengesoknadService.behandleSoknad(soknad)

        periodebekreftelseRepository.findAll().toList().size `should be equal to` 0
    }

    private fun lagreArbeidssokerperiode(): Arbeidssokerperiode =
        arbeidssokerperiodeRepository.save(
            Arbeidssokerperiode(
                fnr = FNR,
                vedtaksperiodeId = VEDTAKSPERIODE_ID,
                // TODO: Send inn verdier for å kunne teste om en søknad er siste eller ikke.
                vedtaksperiodeFom = LocalDate.now().minusMonths(1),
                vedtaksperiodeTom = LocalDate.now().plusMonths(2),
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

    private fun verifiserPeriodebekreftelse(
        lagretPeriode: Arbeidssokerperiode,
        soknad: SykepengesoknadDTO,
        fortsattArbeidssoker: Boolean,
        inntektUnderveis: Boolean,
    ) {
        periodebekreftelseRepository.findAll().toList().single().also {
            it.id `should not be equal to` null
            it.arbeidssokerperiodeId `should be equal to` lagretPeriode.id
            it.sykepengesoknadId `should be equal to` soknad.id
            it.fortsattArbeidssoker `should be equal to` fortsattArbeidssoker
            it.inntektUnderveis `should be equal to` inntektUnderveis
            it.opprettet `should be within seconds of` (1 to Instant.now())
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
