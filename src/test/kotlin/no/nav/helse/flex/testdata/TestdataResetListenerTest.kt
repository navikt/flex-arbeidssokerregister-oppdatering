package no.nav.helse.flex.testdata

import no.nav.helse.flex.FNR
import no.nav.helse.flex.FellesTestOppsett
import no.nav.helse.flex.arbeidssokerperiode.Arbeidssokerperiode
import no.nav.helse.flex.sykepengesoknad.Periodebekreftelse
import no.nav.helse.flex.sykepengesoknad.VedtaksperiodeException
import org.amshove.kluent.`should be equal to`
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
            it.size `should be equal to` 2
        }
    }

    @Test
    @Order(2)
    fun `Lagrer periodebekreftelser tilhørende en arbeidsøkerperiode`() {
        repeat(2) {
            periodebekreftelseRepository.save(
                Periodebekreftelse(
                    arbeidssokerperiodeId = arbeidssokerperiodeRepository.findByFnr(FNR)!!.single().id!!,
                    sykepengesoknadId = UUID.randomUUID().toString(),
                    fortsattArbeidssoker = true,
                    inntektUnderveis = false,
                    opprettet = Instant.now(),
                ),
            )
        }

        periodebekreftelseRepository.save(
            Periodebekreftelse(
                arbeidssokerperiodeId = arbeidssokerperiodeRepository.findByFnr("22222222222")!!.single().id!!,
                sykepengesoknadId = UUID.randomUUID().toString(),
                fortsattArbeidssoker = true,
                inntektUnderveis = false,
                opprettet = Instant.now(),
            ),
        )

        periodebekreftelseRepository.findAll().toList().also {
            it.size `should be equal to` 3
        }
    }

    @Test
    @Order(2)
    fun `Lagrer vedtaksperiodeExceptions for to forskjellige brukere`() {
        vedtaksperiodeExceptionRepository.save(
            VedtaksperiodeException(
                opprettet = Instant.now(),
                vedtaksperiodeId = UUID.randomUUID().toString(),
                sykepengesoknadId = UUID.randomUUID().toString(),
                fnr = FNR,
                exceptionClassName = "Test",
                exceptionMessage = "Test",
            ),
        )

        vedtaksperiodeExceptionRepository.save(
            VedtaksperiodeException(
                opprettet = Instant.now(),
                vedtaksperiodeId = UUID.randomUUID().toString(),
                sykepengesoknadId = UUID.randomUUID().toString(),
                fnr = "22222222222",
                exceptionClassName = "Test",
                exceptionMessage = "Test",
            ),
        )

        vedtaksperiodeExceptionRepository.findAll().toList().also {
            it.size `should be equal to` 2
        }
    }

    @Test
    @Order(3)
    fun `Sletter data for èn bruker ved mottatt melding om testdata reset`() {
        val key = UUID.randomUUID().toString()
        val ikkeSlettetArbeidssokerperiodeId = arbeidssokerperiodeRepository.findByFnr("22222222222")!!.single().id

        kafkaProducer.send(ProducerRecord(TESTDATA_RESET_TOPIC, key, FNR)).get()

        testdataResetConsumer.waitForRecords(1).single().also {
            it.key() `should be equal to` key
            it.value() `should be equal to` FNR
        }

        periodebekreftelseRepository.findAll().toList().single().also {
            it.arbeidssokerperiodeId `should be equal to` ikkeSlettetArbeidssokerperiodeId
        }

        arbeidssokerperiodeRepository.findAll().toList().single().also {
            it.fnr `should be equal to` "22222222222"
        }

        vedtaksperiodeExceptionRepository.findAll().toList().single().also {
            it.fnr `should be equal to` "22222222222"
        }
    }
}
