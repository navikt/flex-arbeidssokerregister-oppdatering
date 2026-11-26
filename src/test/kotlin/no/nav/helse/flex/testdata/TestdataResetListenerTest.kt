package no.nav.helse.flex.testdata

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import no.nav.helse.flex.FNR
import no.nav.helse.flex.FellesTestOppsett
import no.nav.helse.flex.arbeidssokerperiode.Arbeidssokerperiode
import no.nav.helse.flex.sykepengesoknad.Periodebekreftelse
import org.apache.kafka.clients.producer.ProducerRecord
import org.awaitility.Awaitility
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.util.*

class TestdataResetListenerTest : FellesTestOppsett() {
    private val vedtaksperiodeFom = LocalDate.now().minusMonths(1)
    private val vedtaksperiodeTom = LocalDate.now().plusMonths(2)

    @BeforeEach
    @AfterEach
    fun setUp() {
        slettFraDatabase()
    }

    @Test
    fun `Lagrer arbeidssøkerperioder for to brukere`() {
        lagreArbeidssokerperiode(FNR)
        lagreArbeidssokerperiode("22222222222")

        arbeidssokerperiodeRepository.findAll().toList() shouldHaveSize 2
    }

    @Test
    fun `Lagrer periodebekreftelser tilhørende en arbeidsøkerperiode`() {
        val arbeidssokerperiode = lagreArbeidssokerperiode(FNR)
        val annenArbeidssokerperiode = lagreArbeidssokerperiode("22222222222")

        lagrePeriodebekreftelser(arbeidssokerperiode, 2)
        lagrePeriodebekreftelser(annenArbeidssokerperiode, 1)

        periodebekreftelseRepository.findAll().toList() shouldHaveSize 3
    }

    @Test
    fun `Sletter data for èn bruker ved mottatt melding om testdata reset`() {
        val arbeidssokerperiode = lagreArbeidssokerperiode(FNR)
        val ikkeSlettetArbeidssokerperiode = lagreArbeidssokerperiode("22222222222")

        lagrePeriodebekreftelser(arbeidssokerperiode, 2)
        lagrePeriodebekreftelser(ikkeSlettetArbeidssokerperiode, 1)

        val key = UUID.randomUUID().toString()

        kafkaProducer.send(ProducerRecord(TESTDATA_RESET_TOPIC, key, FNR)).get()

        testdataResetConsumer.waitForRecords(1).single().also {
            it.key() shouldBe key
            it.value() shouldBe FNR
        }

        Awaitility.await().atMost(Duration.ofSeconds(5)).untilAsserted {
            periodebekreftelseRepository.findAll().single().also {
                it.arbeidssokerperiodeId shouldBe ikkeSlettetArbeidssokerperiode.id
            }

            arbeidssokerperiodeRepository.findAll().single().also {
                it.id shouldBe ikkeSlettetArbeidssokerperiode.id
                it.fnr shouldBe ikkeSlettetArbeidssokerperiode.fnr
            }
        }
    }

    private fun lagreArbeidssokerperiode(fnr: String): Arbeidssokerperiode =
        arbeidssokerperiodeRepository.save(
            Arbeidssokerperiode(
                fnr = fnr,
                vedtaksperiodeId = UUID.randomUUID().toString(),
                vedtaksperiodeFom = vedtaksperiodeFom,
                vedtaksperiodeTom = vedtaksperiodeTom,
                opprettet = Instant.now(),
            ),
        )

    private fun lagrePeriodebekreftelser(
        arbeidssokerperiode: Arbeidssokerperiode,
        antall: Int,
    ) {
        val arbeidssokerperiodeId = arbeidssokerperiode.id.shouldNotBeNull()
        repeat(antall) {
            periodebekreftelseRepository.save(
                Periodebekreftelse(
                    arbeidssokerperiodeId = arbeidssokerperiodeId,
                    sykepengesoknadId = UUID.randomUUID().toString(),
                    fortsattArbeidssoker = true,
                    inntektUnderveis = false,
                    opprettet = Instant.now(),
                ),
            )
        }
    }
}
