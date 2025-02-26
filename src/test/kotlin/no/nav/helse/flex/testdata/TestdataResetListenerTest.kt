package no.nav.helse.flex.testdata

import no.nav.helse.flex.Arbeidssokerperiode
import no.nav.helse.flex.ArbeidssokerperiodeRepository
import no.nav.helse.flex.FNR
import no.nav.helse.flex.FellesTestOppsett
import no.nav.helse.flex.Periodebekreftelse
import no.nav.helse.flex.PeriodebekreftelseRepository
import org.amshove.kluent.`should be equal to`
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerRecord
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.springframework.beans.factory.annotation.Autowired
import java.time.Instant
import java.util.*

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class TestdataResetListenerTest : FellesTestOppsett() {
    @Autowired
    private lateinit var arbeidssokerperiodeRepository: ArbeidssokerperiodeRepository

    @Autowired
    private lateinit var periodebekreftelseRepository: PeriodebekreftelseRepository

    @Autowired
    private lateinit var kafkaProducer: Producer<String, String>

    @Autowired
    private lateinit var testdataResetConsumer: Consumer<String, String>

    @BeforeAll
    fun subscribeToTopics() {
        testdataResetConsumer.subscribeToTopics(TESTDATA_RESET_TOPIC)
    }

    @BeforeAll
    fun slettFraDatabase() {
        periodebekreftelseRepository.deleteAll()
        arbeidssokerperiodeRepository.deleteAll()
    }

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

        testdataResetConsumer.waitForRecords(1).also {
            it.first().key() `should be equal to` key
            it.first().value() `should be equal to` FNR
        }

        periodebekreftelseRepository.findAll().toList().single().also {
            it.arbeidssokerperiodeId `should be equal to` ikkeSlettetArbeidssokerperiodeId
        }

        arbeidssokerperiodeRepository.findAll().toList().single().also {
            it.fnr `should be equal to` "22222222222"
        }
    }
}
