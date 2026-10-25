package no.nav.helse.flex.testdata

import io.kotest.matchers.shouldBe
import no.nav.helse.flex.FNR
import no.nav.helse.flex.FellesTestOppsett
import no.nav.helse.flex.arbeidssokerperiode.Arbeidssokerperiode
import no.nav.helse.flex.sykepengesoknad.Periodebekreftelse
import org.apache.kafka.clients.producer.ProducerRecord
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import java.time.Instant
import java.time.LocalDate
import java.util.*

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class TestdataResetListenerTest : FellesTestOppsett() {
    private val vedtaksperiodeFom = LocalDate.now().minusMonths(1)
    private val vedtaksperiodeTom = LocalDate.now().plusMonths(2)

    @Test
    @Order(1)
    fun `Lagrer arbeidssøkerperioder for to brukere`() {
        arbeidssokerperiodeRepository.save(
            Arbeidssokerperiode(
                fnr = FNR,
                vedtaksperiodeId = UUID.randomUUID().toString(),
                vedtaksperiodeFom = vedtaksperiodeFom,
                vedtaksperiodeTom = vedtaksperiodeTom,
                opprettet = Instant.now(),
            ),
        )

        arbeidssokerperiodeRepository.save(
            Arbeidssokerperiode(
                fnr = "22222222222",
                vedtaksperiodeId = UUID.randomUUID().toString(),
                vedtaksperiodeFom = vedtaksperiodeFom,
                vedtaksperiodeTom = vedtaksperiodeTom,
                opprettet = Instant.now(),
            ),
        )

        arbeidssokerperiodeRepository.findAll().toList().also {
            it.size shouldBe 2
        }
    }

    @Test
    @Order(2)
    fun `Lagrer periodebekreftelser tilhørende en arbeidsøkerperiode`() {
        repeat(2) {
            periodebekreftelseRepository.save(
                Periodebekreftelse(
                    arbeidssokerperiodeId = arbeidssokerperiodeRepository.findByFnr(FNR).single().id!!,
                    sykepengesoknadId = UUID.randomUUID().toString(),
                    fortsattArbeidssoker = true,
                    inntektUnderveis = false,
                    opprettet = Instant.now(),
                ),
            )
        }

        periodebekreftelseRepository.save(
            Periodebekreftelse(
                arbeidssokerperiodeId = arbeidssokerperiodeRepository.findByFnr("22222222222").single().id!!,
                sykepengesoknadId = UUID.randomUUID().toString(),
                fortsattArbeidssoker = true,
                inntektUnderveis = false,
                opprettet = Instant.now(),
            ),
        )

        periodebekreftelseRepository.findAll().toList().also {
            it.size shouldBe 3
        }
    }

    @Test
    @Order(3)
    fun `Sletter data for èn bruker ved mottatt melding om testdata reset`() {
        val key = UUID.randomUUID().toString()
        val ikkeSlettetArbeidssokerperiodeId = arbeidssokerperiodeRepository.findByFnr("22222222222").single().id

        kafkaProducer.send(ProducerRecord(TESTDATA_RESET_TOPIC, key, FNR)).get()

        testdataResetConsumer.waitForRecords(1).single().also {
            it.key() shouldBe key
            it.value() shouldBe FNR
        }

        periodebekreftelseRepository.findAll().toList().single().also {
            it.arbeidssokerperiodeId shouldBe ikkeSlettetArbeidssokerperiodeId
        }

        arbeidssokerperiodeRepository.findAll().toList().single().also {
            it.fnr shouldBe "22222222222"
        }
    }
}
