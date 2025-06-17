package no.nav.helse.flex.arbeidssokerperiode

import no.nav.helse.flex.FNR
import no.nav.helse.flex.FellesTestOppsett
import no.nav.helse.flex.`should be within seconds of`
import no.nav.helse.flex.sykepengesoknad.asProducerRecordKey
import no.nav.helse.flex.sykepengesoknad.tilArbeidssokerperiodeStoppMelding
import no.nav.helse.flex.sykepengesoknad.toInstantAtStartOfDay
import no.nav.paw.arbeidssokerregisteret.api.v1.Bruker
import no.nav.paw.arbeidssokerregisteret.api.v1.BrukerType
import no.nav.paw.arbeidssokerregisteret.api.v1.Metadata
import no.nav.paw.arbeidssokerregisteret.api.v1.Periode
import org.amshove.kluent.`should be equal to`
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import java.time.Instant
import java.time.LocalDate
import java.util.*

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
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

    @Test
    fun `Behandler kjent avsluttet Periode`() {
        lagreArbeidsokerperiode(vedtaksperiodeId)

        lagKafkaPeriode(arbeidssokerregisterperiodeId, true).also {
            arbeidssokerperiodeService.behandlePeriode(it)
        }

        arbeidssokerperiodeRepository.findByArbeidssokerperiodeId(arbeidssokerregisterperiodeId).single().also {
            it.avsluttetMottatt!! `should be within seconds of` (1 to Instant.now())
            it.avsluttetTidspunkt `should be equal to` avsluttetTidspunkt
            it.vedtaksperiodeFom `should be equal to` vedtaksperiodeFom
            it.vedtaksperiodeTom `should be equal to` vedtaksperiodeTom
        }

        arbeidssokerperiodeStoppConsumer.waitForRecords(1).single().also { consumerRecord ->
            consumerRecord.key() `should be equal to` FNR.asProducerRecordKey()

            consumerRecord.value().tilArbeidssokerperiodeStoppMelding().also {
                it.vedtaksperiodeId `should be equal to` vedtaksperiodeId
                it.fnr `should be equal to` FNR
                it.avsluttetTidspunkt `should be equal to` avsluttetTidspunkt
            }
        }
    }

    @Test
    fun `Behandler ikke uavsluttet Periode`() {
        lagreArbeidsokerperiode(vedtaksperiodeId)

        lagKafkaPeriode(arbeidssokerregisterperiodeId, false).also {
            arbeidssokerperiodeService.behandlePeriode(it)
        }

        arbeidssokerperiodeRepository.findByArbeidssokerperiodeId(arbeidssokerregisterperiodeId).single().also {
            it.avsluttetMottatt `should be equal to` null
            it.avsluttetTidspunkt `should be equal to` null
        }
    }

    @Test
    fun `Behandler ikke avsluttet Periode to ganger`() {
        lagreArbeidsokerperiode(vedtaksperiodeId)

        lagKafkaPeriode(arbeidssokerregisterperiodeId, true).also {
            arbeidssokerperiodeService.behandlePeriode(it)
            arbeidssokerperiodeService.behandlePeriode(it)
        }

        arbeidssokerperiodeRepository.findByArbeidssokerperiodeId(arbeidssokerregisterperiodeId).single().also {
            it.avsluttetMottatt!! `should be within seconds of` (1 to Instant.now())
            it.avsluttetTidspunkt `should be equal to` avsluttetTidspunkt
        }

        arbeidssokerperiodeStoppConsumer.waitForRecords(1).single().also { consumerRecord ->
            consumerRecord.key() `should be equal to` FNR.asProducerRecordKey()

            consumerRecord.value().tilArbeidssokerperiodeStoppMelding().also {
                it.vedtaksperiodeId `should be equal to` vedtaksperiodeId
                it.fnr `should be equal to` FNR
                it.avsluttetTidspunkt `should be equal to` avsluttetTidspunkt
            }
        }
    }

    @Test
    fun `Behandler ikke ukjent Periode`() {
        lagKafkaPeriode(UUID.randomUUID().toString(), true).also {
            arbeidssokerperiodeService.behandlePeriode(it)
        }

        arbeidssokerperiodeRepository
            .findByArbeidssokerperiodeId(arbeidssokerregisterperiodeId)
            .isEmpty() `should be equal to` true
    }

    @Test
    fun `Behandler flere arbeidssokerperioder med samme Periode`() {
        lagreArbeidsokerperiode(vedtaksperiodeId)

        val vedtaksperiodeId2 = UUID.randomUUID().toString()
        lagreArbeidsokerperiode(vedtaksperiodeId2)

        lagKafkaPeriode(arbeidssokerregisterperiodeId, true).also {
            arbeidssokerperiodeService.behandlePeriode(it)
        }

        val arbeidssokerperioder = arbeidssokerperiodeRepository.findByArbeidssokerperiodeId(arbeidssokerregisterperiodeId)
        arbeidssokerperioder.size `should be equal to` 2

        arbeidssokerperioder.forEach { arbeidssokerperiode ->
            arbeidssokerperiode.avsluttetMottatt!! `should be within seconds of` (1 to Instant.now())
            arbeidssokerperiode.avsluttetTidspunkt `should be equal to` avsluttetTidspunkt
        }

        arbeidssokerperiodeStoppConsumer.waitForRecords(2).also { consumerRecords ->
            consumerRecords.size `should be equal to` 2

            val vedtaksperiodeIds = consumerRecords.map { it.value().tilArbeidssokerperiodeStoppMelding().vedtaksperiodeId }
            vedtaksperiodeIds.containsAll(listOf(vedtaksperiodeId, vedtaksperiodeId2)) `should be equal to` true
        }
    }

    private fun lagreArbeidsokerperiode(vedtaksperiodeId: String) {
        Arbeidssokerperiode(
            fnr = FNR,
            vedtaksperiodeId = vedtaksperiodeId,
            vedtaksperiodeFom = vedtaksperiodeFom,
            vedtaksperiodeTom = vedtaksperiodeTom,
            opprettet = Instant.now(),
            kafkaRecordKey = -3771L,
            arbeidssokerperiodeId = arbeidssokerregisterperiodeId,
            sendtPaaVegneAv = Instant.now(),
        ).also {
            arbeidssokerperiodeRepository.save(it)
        }
    }

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
