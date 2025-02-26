package no.nav.helse.flex.testdata

import no.nav.helse.flex.Arbeidssokerperiode
import no.nav.helse.flex.FNR
import no.nav.helse.flex.FellesTestOppsett
import no.nav.helse.flex.Periodebekreftelse
import org.amshove.kluent.`should be equal to`
import org.apache.kafka.clients.producer.ProducerRecord
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import java.time.Instant
import java.util.*

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class TestdataResetListenerTest : FellesTestOppsett() {
    @Test
    @Order(1)
    fun `Lagrer arbeidssøkerperioder for to brukere`() {
        arbeidssokerperiodeRepository.save(
            Arbeidssokerperiode(
                fnr = FNR,
                vedtaksperiodeId = UUID.randomUUID().toString(),
                opprettet = Instant.now(),
            ),
        )

        arbeidssokerperiodeRepository.save(
            Arbeidssokerperiode(
                fnr = "22222222222",
                vedtaksperiodeId = UUID.randomUUID().toString(),
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
    }
}
