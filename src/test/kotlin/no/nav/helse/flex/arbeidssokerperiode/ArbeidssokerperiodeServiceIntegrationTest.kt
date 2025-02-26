package no.nav.helse.flex.arbeidssokerperiode

import no.nav.helse.flex.Arbeidssokerperiode
import no.nav.helse.flex.ArbeidssokerperiodeRepository
import no.nav.helse.flex.FNR
import no.nav.helse.flex.FellesTestOppsett
import no.nav.helse.flex.`should be within seconds of`
import no.nav.helse.flex.sykepengesoknad.ARBEIDSSOKERPERIODE_STOPP_TOPIC
import no.nav.helse.flex.sykepengesoknad.asProducerRecordKey
import no.nav.helse.flex.sykepengesoknad.tilArbeidssokerperiodeStoppMelding
import no.nav.paw.arbeidssokerregisteret.api.v1.Bruker
import no.nav.paw.arbeidssokerregisteret.api.v1.BrukerType
import no.nav.paw.arbeidssokerregisteret.api.v1.Metadata
import no.nav.paw.arbeidssokerregisteret.api.v1.Periode
import org.amshove.kluent.`should be equal to`
import org.apache.kafka.clients.consumer.Consumer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.springframework.beans.factory.annotation.Autowired
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.*

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class ArbeidssokerperiodeServiceIntegrationTest : FellesTestOppsett() {
    @Autowired
    private lateinit var arbeidssokerperiodeRepository: ArbeidssokerperiodeRepository

    @Autowired
    private lateinit var arbeidssokerperiodeService: ArbeidssokerperiodeService

    @Autowired
    private lateinit var arbeidssokerperiodeStoppConsumer: Consumer<String, String>

    @BeforeAll
    fun subscribeToTopics() {
        arbeidssokerperiodeStoppConsumer.subscribeToTopics(ARBEIDSSOKERPERIODE_STOPP_TOPIC)
    }

    @BeforeEach
    fun slettFraDatabase() {
        arbeidssokerperiodeRepository.deleteAll()
    }

    @AfterEach
    fun verifiserAtTopicEtTomt() {
        arbeidssokerperiodeStoppConsumer.fetchRecords().size `should be equal to` 0
    }

    private val startetTidspunkt = LocalDate.of(2025, 1, 2).atStartOfDay().toInstant(ZoneOffset.UTC)
    private val avsluttetTidspunkt = LocalDate.of(2025, 1, 31).atStartOfDay().toInstant(ZoneOffset.UTC)
    private val vedtaksperiodeId = UUID.randomUUID().toString()
    private val arbeidssokerperiodeId = UUID.randomUUID().toString()

    @Test
    fun `Behandler kjent avsluttet Periode`() {
        lagreArbeidsokerperiode(vedtaksperiodeId, arbeidssokerperiodeId)

        lagKafkaPeriode(arbeidssokerperiodeId, true).also {
            arbeidssokerperiodeService.behandlePeriode(it)
        }

        arbeidssokerperiodeRepository.findByArbeidssokerperiodeId(arbeidssokerperiodeId)!!.also {
            it.avsluttetMottatt!! `should be within seconds of` (1 to Instant.now())
            it.avsluttetTidspunkt `should be equal to` avsluttetTidspunkt
        }

        arbeidssokerperiodeStoppConsumer.waitForRecords(1).single().also {
            it.key() `should be equal to` FNR.asProducerRecordKey()

            it.value().tilArbeidssokerperiodeStoppMelding().also {
                it.vedtaksperiodeId `should be equal to` vedtaksperiodeId
                it.fnr `should be equal to` FNR
                it.avsluttetTidspunkt `should be equal to` avsluttetTidspunkt
            }
        }
    }

    @Test
    fun `Behandler ikke uavsluttet Periode`() {
        lagreArbeidsokerperiode(vedtaksperiodeId, arbeidssokerperiodeId)

        lagKafkaPeriode(arbeidssokerperiodeId, false).also {
            arbeidssokerperiodeService.behandlePeriode(it)
        }

        arbeidssokerperiodeRepository.findByArbeidssokerperiodeId(arbeidssokerperiodeId)!!.also {
            it.avsluttetMottatt `should be equal to` null
            it.avsluttetTidspunkt `should be equal to` null
        }
    }

    @Test
    fun `Behandler ikke avsluttet Periode to ganger`() {
        lagreArbeidsokerperiode(vedtaksperiodeId, arbeidssokerperiodeId)

        lagKafkaPeriode(arbeidssokerperiodeId, true).also {
            arbeidssokerperiodeService.behandlePeriode(it)
            arbeidssokerperiodeService.behandlePeriode(it)
        }

        arbeidssokerperiodeRepository.findByArbeidssokerperiodeId(arbeidssokerperiodeId)!!.also {
            it.avsluttetMottatt!! `should be within seconds of` (1 to Instant.now())
            it.avsluttetTidspunkt `should be equal to` avsluttetTidspunkt
        }

        arbeidssokerperiodeStoppConsumer.waitForRecords(1).single().also {
            it.key() `should be equal to` FNR.asProducerRecordKey()

            it.value().tilArbeidssokerperiodeStoppMelding().also {
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

        arbeidssokerperiodeRepository.findByArbeidssokerperiodeId(arbeidssokerperiodeId) `should be equal to` null
    }

    private fun lagreArbeidsokerperiode(
        vedtaksperiodeId: String,
        arbeidssokerperiodeId: String,
    ) {
        Arbeidssokerperiode(
            fnr = FNR,
            vedtaksperiodeId = vedtaksperiodeId,
            opprettet = Instant.now(),
            kafkaRecordKey = -3771L,
            arbeidssokerperiodeId = arbeidssokerperiodeId,
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
                Bruker(BrukerType.SLUTTBRUKER, FNR),
                "paw-arbeidssokerregisteret-api-utgang",
                "Test",
                null,
            )
        return Periode(
            UUID.fromString(arbeidssokerperiodeId),
            FNR,
            Metadata(
                startetTidspunkt,
                Bruker(BrukerType.SLUTTBRUKER, FNR),
                "paw-arbeidssokerregisteret-api-inngang",
                "Test",
                null,
            ),
            if (erAvsluttet) avsluttet else null,
        )
    }
}
