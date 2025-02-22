package no.nav.helse.flex.sykepengesoknad

import no.nav.helse.flex.FellesTestOppsett
import no.nav.helse.flex.serialisertTilString
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadsstatusDTO
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadstypeDTO
import no.nav.helse.flex.sykepengesoknad.kafka.SykepengesoknadDTO
import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.`should not be`
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerRecord
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDate
import java.util.*
import java.util.concurrent.TimeUnit

class SykepengesoknadListenerTest : FellesTestOppsett() {
    @Autowired
    private lateinit var sykepengesoknadListener: SykepengesoknadListener

    @Autowired
    private lateinit var kafkaProducer: Producer<String, String>

    @Autowired
    private lateinit var sykepengesoknadConsumer: Consumer<String, String>

    @BeforeAll
    fun subscribeToTopics() {
        sykepengesoknadConsumer.subscribeToTopics(SYKEPENGESOKNAD_TOPIC)
    }

    @Test
    fun `Sender og mottar SykepengesoknadDTO`() {
        val key = UUID.randomUUID().toString()
        val fnr = "11111111111"

        val soknad =
            SykepengesoknadDTO(
                fnr = fnr,
                id = key,
                type = SoknadstypeDTO.FRISKMELDT_TIL_ARBEIDSFORMIDLING,
                status = SoknadsstatusDTO.FREMTIDIG,
                fom = LocalDate.now(),
                tom = LocalDate.now().plusDays(13),
            )

        kafkaProducer.send(ProducerRecord(SYKEPENGESOKNAD_TOPIC, key, soknad.serialisertTilString())).get()

        await().atMost(1, TimeUnit.SECONDS).untilAsserted {
            sykepengesoknadListener.hentSoknad(key) `should not be` null
        }

        sykepengesoknadConsumer.waitForRecords(1).first().also {
            it.key() `should be equal to` key

            it.value().tilSykepengesoknadDTO().also {
                it.type `should be equal to` SoknadstypeDTO.FRISKMELDT_TIL_ARBEIDSFORMIDLING
                it.id `should be equal to` key
                it.status `should be equal to` SoknadsstatusDTO.FREMTIDIG
                it.fnr `should be equal to` fnr
            }
        }
    }
}
