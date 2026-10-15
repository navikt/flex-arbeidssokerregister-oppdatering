package no.nav.helse.flex.arbeidssokerperiode

import no.nav.helse.flex.FNR
import no.nav.helse.flex.FellesTestOppsett
import no.nav.helse.flex.`should be within seconds of`
import no.nav.helse.flex.sykepengesoknad.SOKNAD_DEAKTIVERES_ETTER_MAANEDER
import no.nav.helse.flex.sykepengesoknad.StartStop
import no.nav.helse.flex.sykepengesoknad.asProducerRecordKey
import no.nav.helse.flex.sykepengesoknad.beregnGraceMS
import no.nav.helse.flex.sykepengesoknad.tilArbeidssokerperiodeStartStoppMelding
import no.nav.helse.flex.sykepengesoknad.toInstantAtStartOfDay
import no.nav.paw.arbeidssokerregisteret.api.v1.Bruker
import no.nav.paw.arbeidssokerregisteret.api.v1.BrukerType
import no.nav.paw.arbeidssokerregisteret.api.v1.Metadata
import no.nav.paw.arbeidssokerregisteret.api.v1.Periode
import no.nav.paw.bekreftelse.paavegneav.v1.vo.Start
import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.shouldBeEmpty
import org.amshove.kluent.shouldBeNull
import org.amshove.kluent.shouldContainAll
import org.amshove.kluent.shouldHaveSize
import org.amshove.kluent.shouldNotBeNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.util.*

class ArbeidssokerperiodeServiceIntegrationTest : FellesTestOppsett() {
    @BeforeEach
    fun setup() {
        arbeidssokerperiodeRepository.deleteAll()
    }

    private val startetTidspunkt = LocalDate.of(2025, 1, 2).toInstantAtStartOfDay()
    private val avsluttetTidspunkt = LocalDate.of(2025, 1, 31).toInstantAtStartOfDay()
    private val vedtaksperiodeId = UUID.randomUUID().toString()
    private val arbeidssokerregisterperiodeId = UUID.randomUUID().toString()
    private val vedtaksperiodeFom = LocalDate.now().minusMonths(1)
    private val vedtaksperiodeTom = LocalDate.now().plusMonths(2)
    private val kafkaRecordKey = -3771L

    @Nested
    inner class AvsluttetPeriode {
        @Test
        fun `Avslutter arbeidssøkerperiode med samme periode`() {
            lagreArbeidssokerperiode()

            behandleAvsluttetPeriode(arbeidssokerregisterperiodeId)

            hentArbeidssokerperiodeMedPeriodeId(arbeidssokerregisterperiodeId).also {
                it.avsluttetMottatt!! `should be within seconds of` (1 to Instant.now())
                it.avsluttetTidspunkt `should be equal to` avsluttetTidspunkt
                it.vedtaksperiodeFom `should be equal to` vedtaksperiodeFom
                it.vedtaksperiodeTom `should be equal to` vedtaksperiodeTom
            }

            verifiserStartStoppMelding(StartStop.STOPP, avsluttetTidspunkt)
        }

        @Test
        fun `Avslutter ikke arbeidssøkerperiode to ganger når samme periode mottas på nytt`() {
            lagreArbeidssokerperiode()

            repeat(2) { behandleAvsluttetPeriode(arbeidssokerregisterperiodeId) }

            hentArbeidssokerperiodeMedPeriodeId(arbeidssokerregisterperiodeId).also {
                it.avsluttetMottatt!! `should be within seconds of` (1 to Instant.now())
                it.avsluttetTidspunkt `should be equal to` avsluttetTidspunkt
            }

            verifiserStartStoppMelding(StartStop.STOPP, avsluttetTidspunkt)
        }

        @Test
        fun `Avslutter ikke når arbeidssøkerperiode med samme periode ikke finnes`() {
            behandleAvsluttetPeriode(UUID.randomUUID().toString())

            arbeidssokerperiodeRepository.findByArbeidssokerperiodeId(arbeidssokerregisterperiodeId).shouldBeEmpty()
        }

        @Test
        fun `Avslutter alle arbeidssøkerperioder med samme periode`() {
            lagreArbeidssokerperiode()
            val vedtaksperiodeId2 = UUID.randomUUID().toString()
            lagreArbeidssokerperiode(vedtaksperiodeId2)

            behandleAvsluttetPeriode(arbeidssokerregisterperiodeId)

            arbeidssokerperiodeRepository.findByArbeidssokerperiodeId(arbeidssokerregisterperiodeId).also {
                it shouldHaveSize 2
                it.forEach { arbeidssokerperiode ->
                    arbeidssokerperiode.avsluttetMottatt!! `should be within seconds of` (1 to Instant.now())
                    arbeidssokerperiode.avsluttetTidspunkt `should be equal to` avsluttetTidspunkt
                }
            }

            arbeidssokerperiodeStartStoppConsumer.waitForRecords(2).also { consumerRecords ->
                consumerRecords shouldHaveSize 2
                consumerRecords.map { it.value().tilArbeidssokerperiodeStartStoppMelding().vedtaksperiodeId } shouldContainAll
                    listOf(vedtaksperiodeId, vedtaksperiodeId2)
            }
        }
    }

    @Nested
    inner class StartetPeriode {
        @Test
        fun `Restarter avsluttet arbeidssøkerperiode med ny periode`() {
            val avsluttetArbeidssokerperiode = lagreAvsluttetArbeidssokerperiode()
            val nyArbeidssokerregisterperiodeId = UUID.randomUUID().toString()

            behandleStartetPeriode(nyArbeidssokerregisterperiodeId)

            hentArbeidssokerperiode(avsluttetArbeidssokerperiode.id!!).also {
                it.arbeidssokerperiodeId `should be equal to` nyArbeidssokerregisterperiodeId
                it.avsluttetMottatt.shouldBeNull()
                it.avsluttetTidspunkt.shouldBeNull()
                it.sendtAvsluttet.shouldBeNull()
                it.avsluttetAarsak.shouldBeNull()
                it.sendtPaaVegneAv!! `should be within seconds of` (1 to Instant.now())
                it.vedtaksperiodeId `should be equal to` vedtaksperiodeId
                it.vedtaksperiodeFom `should be equal to` vedtaksperiodeFom
                it.vedtaksperiodeTom `should be equal to` vedtaksperiodeTom
                it.kafkaRecordKey `should be equal to` kafkaRecordKey
            }

            verifiserPaaVegneAvStartMelding(nyArbeidssokerregisterperiodeId)
            verifiserStartStoppMelding(StartStop.START, startetTidspunkt)
        }

        @Test
        fun `Restarter ikke arbeidssøkerperiode to ganger når samme periode mottas på nytt`() {
            lagreAvsluttetArbeidssokerperiode()
            val nyArbeidssokerregisterperiodeId = UUID.randomUUID().toString()

            repeat(2) { behandleStartetPeriode(nyArbeidssokerregisterperiodeId) }

            paaVegneAvConsumer.waitForRecords(1).single()
            arbeidssokerperiodeStartStoppConsumer.waitForRecords(1).single()
        }

        @Test
        fun `Restarter ikke når periode er den samme som avsluttet arbeidssøkerperiode har`() {
            lagreAvsluttetArbeidssokerperiode()

            behandleStartetPeriode(arbeidssokerregisterperiodeId)

            hentArbeidssokerperiodeMedPeriodeId(arbeidssokerregisterperiodeId).also {
                it.avsluttetMottatt.shouldNotBeNull()
                it.avsluttetTidspunkt `should be equal to` avsluttetTidspunkt
            }
            verifiserIngenMeldingerSendt()
        }

        @Test
        fun `Restarter ikke når periode er den samme som aktiv arbeidssøkerperiode har`() {
            lagreArbeidssokerperiode()

            behandleStartetPeriode(arbeidssokerregisterperiodeId)

            hentArbeidssokerperiodeMedPeriodeId(arbeidssokerregisterperiodeId).also {
                it.avsluttetMottatt.shouldBeNull()
                it.avsluttetTidspunkt.shouldBeNull()
            }
            verifiserIngenMeldingerSendt()
        }

        @Test
        fun `Restarter ikke når arbeidssøkerperiode ikke finnes`() {
            behandleStartetPeriode(UUID.randomUUID().toString())

            arbeidssokerperiodeRepository.findAll().shouldBeEmpty()
            verifiserIngenMeldingerSendt()
        }

        @Test
        fun `Restarter ikke når siste arbeidssøkerperiode ikke er avsluttet`() {
            lagreArbeidssokerperiode()

            behandleStartetPeriode(UUID.randomUUID().toString())

            hentArbeidssokerperiodeMedPeriodeId(arbeidssokerregisterperiodeId).avsluttetMottatt.shouldBeNull()
            verifiserIngenMeldingerSendt()
        }

        @Test
        fun `Restarter ikke når siste arbeidssøkerperiode er avsluttet av bruker`() {
            lagreAvsluttetArbeidssokerperiode(
                sendtAvsluttet = Instant.now(),
                avsluttetAarsak = AvsluttetAarsak.BRUKER,
            )

            behandleStartetPeriode(UUID.randomUUID().toString())

            hentArbeidssokerperiodeMedPeriodeId(arbeidssokerregisterperiodeId).avsluttetMottatt.shouldNotBeNull()
            verifiserIngenMeldingerSendt()
        }

        @Test
        fun `Restarter ikke når vedtaksperiodeTom er eldre enn fire måneder`() {
            lagreAvsluttetArbeidssokerperiode(
                vedtaksperiodeTom = LocalDate.now().minusMonths(PERIODE_TOM_MAX_ALDER_MAANEDER.toLong()).minusDays(1),
            )

            behandleStartetPeriode(UUID.randomUUID().toString())

            hentArbeidssokerperiodeMedPeriodeId(arbeidssokerregisterperiodeId).avsluttetMottatt.shouldNotBeNull()
            verifiserIngenMeldingerSendt()
        }

        @Test
        fun `Restarter når vedtaksperiodeTom er nøyaktig fire måneder siden`() {
            val tom = LocalDate.now().minusMonths(PERIODE_TOM_MAX_ALDER_MAANEDER.toLong())
            val avsluttetArbeidssokerperiode = lagreAvsluttetArbeidssokerperiode(vedtaksperiodeTom = tom)
            val nyArbeidssokerregisterperiodeId = UUID.randomUUID().toString()

            behandleStartetPeriode(nyArbeidssokerregisterperiodeId)

            hentArbeidssokerperiode(avsluttetArbeidssokerperiode.id!!).also {
                it.arbeidssokerperiodeId `should be equal to` nyArbeidssokerregisterperiodeId
                it.avsluttetMottatt.shouldBeNull()
            }

            verifiserPaaVegneAvStartMelding(nyArbeidssokerregisterperiodeId, tom)
            verifiserStartStoppMelding(StartStop.START, startetTidspunkt)
        }

        @Test
        fun `Restarter kun siste arbeidssøkerperiode`() {
            val eldreArbeidssokerperiode =
                lagreAvsluttetArbeidssokerperiode(
                    vedtaksperiodeId = UUID.randomUUID().toString(),
                    vedtaksperiodeTom = vedtaksperiodeTom.minusMonths(1),
                )
            val sisteArbeidssokerperiode = lagreAvsluttetArbeidssokerperiode()
            val nyArbeidssokerregisterperiodeId = UUID.randomUUID().toString()

            behandleStartetPeriode(nyArbeidssokerregisterperiodeId)

            hentArbeidssokerperiode(sisteArbeidssokerperiode.id!!).also {
                it.arbeidssokerperiodeId `should be equal to` nyArbeidssokerregisterperiodeId
                it.avsluttetMottatt.shouldBeNull()
            }

            hentArbeidssokerperiode(eldreArbeidssokerperiode.id!!).also {
                it.arbeidssokerperiodeId `should be equal to` arbeidssokerregisterperiodeId
                it.avsluttetMottatt.shouldNotBeNull()
            }

            verifiserPaaVegneAvStartMelding(nyArbeidssokerregisterperiodeId)
            verifiserStartStoppMelding(StartStop.START, startetTidspunkt)
        }
    }

    private fun behandleStartetPeriode(arbeidssokerregisterperiodeId: String) =
        arbeidssokerperiodeService.behandlePeriode(lagKafkaPeriode(arbeidssokerregisterperiodeId, erAvsluttet = false))

    private fun behandleAvsluttetPeriode(arbeidssokerregisterperiodeId: String) =
        arbeidssokerperiodeService.behandlePeriode(lagKafkaPeriode(arbeidssokerregisterperiodeId, erAvsluttet = true))

    private fun hentArbeidssokerperiode(id: String): Arbeidssokerperiode = arbeidssokerperiodeRepository.findById(id).get()

    private fun hentArbeidssokerperiodeMedPeriodeId(arbeidssokerregisterperiodeId: String): Arbeidssokerperiode =
        arbeidssokerperiodeRepository.findByArbeidssokerperiodeId(arbeidssokerregisterperiodeId).single()

    private fun verifiserStartStoppMelding(
        operation: StartStop,
        tidspunkt: Instant,
    ) {
        arbeidssokerperiodeStartStoppConsumer.waitForRecords(1).single().also { consumerRecord ->
            consumerRecord.key() `should be equal to` FNR.asProducerRecordKey()

            consumerRecord.value().tilArbeidssokerperiodeStartStoppMelding().also {
                it.operation `should be equal to` operation
                it.vedtaksperiodeId `should be equal to` vedtaksperiodeId
                it.fnr `should be equal to` FNR
                it.tidspunkt `should be equal to` tidspunkt
            }
        }
    }

    private fun verifiserPaaVegneAvStartMelding(
        arbeidssokerregisterperiodeId: String,
        vedtaksperiodeTom: LocalDate = this.vedtaksperiodeTom,
    ) {
        paaVegneAvConsumer.waitForRecords(1).single().also {
            it.key() `should be equal to` kafkaRecordKey
            it.value().periodeId `should be equal to` UUID.fromString(arbeidssokerregisterperiodeId)
            (it.value().handling as Start).graceMS `should be equal to`
                beregnGraceMS(vedtaksperiodeTom, SOKNAD_DEAKTIVERES_ETTER_MAANEDER)
        }
    }

    private fun verifiserIngenMeldingerSendt() {
        paaVegneAvConsumer.fetchRecords().shouldBeEmpty()
        arbeidssokerperiodeStartStoppConsumer.fetchRecords().shouldBeEmpty()
    }

    private fun lagreArbeidssokerperiode(
        vedtaksperiodeId: String = this.vedtaksperiodeId,
        vedtaksperiodeTom: LocalDate = this.vedtaksperiodeTom,
        avsluttetMottatt: Instant? = null,
        avsluttetTidspunkt: Instant? = null,
        sendtAvsluttet: Instant? = null,
        avsluttetAarsak: AvsluttetAarsak? = null,
    ): Arbeidssokerperiode =
        Arbeidssokerperiode(
            fnr = FNR,
            vedtaksperiodeId = vedtaksperiodeId,
            vedtaksperiodeFom = vedtaksperiodeFom,
            vedtaksperiodeTom = vedtaksperiodeTom,
            opprettet = Instant.now(),
            kafkaRecordKey = kafkaRecordKey,
            arbeidssokerperiodeId = arbeidssokerregisterperiodeId,
            sendtPaaVegneAv = Instant.now(),
            avsluttetMottatt = avsluttetMottatt,
            avsluttetTidspunkt = avsluttetTidspunkt,
            sendtAvsluttet = sendtAvsluttet,
            avsluttetAarsak = avsluttetAarsak,
        ).let(arbeidssokerperiodeRepository::save)

    private fun lagreAvsluttetArbeidssokerperiode(
        vedtaksperiodeId: String = this.vedtaksperiodeId,
        vedtaksperiodeTom: LocalDate = this.vedtaksperiodeTom,
        sendtAvsluttet: Instant? = null,
        avsluttetAarsak: AvsluttetAarsak? = null,
    ): Arbeidssokerperiode =
        lagreArbeidssokerperiode(
            vedtaksperiodeId = vedtaksperiodeId,
            vedtaksperiodeTom = vedtaksperiodeTom,
            avsluttetMottatt = Instant.now(),
            avsluttetTidspunkt = avsluttetTidspunkt,
            sendtAvsluttet = sendtAvsluttet,
            avsluttetAarsak = avsluttetAarsak,
        )

    private fun lagKafkaPeriode(
        arbeidssokerperiodeId: String,
        erAvsluttet: Boolean = false,
    ): Periode {
        val avsluttet =
            Metadata(
                avsluttetTidspunkt,
                // Sender sikkerhetsnivaa siden meldingen er basert på melding på Kafka.
                Bruker(BrukerType.SLUTTBRUKER, FNR, null),
                "paw-arbeidssokerregisteret-api-utgang",
                "Test",
                null,
            )
        return Periode(
            UUID.fromString(arbeidssokerperiodeId),
            FNR,
            Metadata(
                startetTidspunkt,
                Bruker(BrukerType.SLUTTBRUKER, FNR, null),
                "paw-arbeidssokerregisteret-api-inngang",
                "Test",
                null,
            ),
            if (erAvsluttet) avsluttet else null,
        )
    }
}
