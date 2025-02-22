package no.nav.helse.flex.sykepengesoknad

import no.nav.helse.flex.ArbeidssokerperiodeRepository
import no.nav.helse.flex.FNR
import no.nav.helse.flex.FellesTestOppsett
import no.nav.helse.flex.VEDTAKSPERIODE_ID
import no.nav.helse.flex.lagFremtidigFriskTilArbeidSoknad
import no.nav.helse.flex.serialisertTilString
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadsstatusDTO
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadstypeDTO
import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.`should not be equal to`
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerRecord
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.util.*

class SykepengesoknadListenerIntegrationTest : FellesTestOppsett() {
    @Autowired
    private lateinit var kafkaProducer: Producer<String, String>

    @Autowired
    private lateinit var arbeidssokerperiodeRepository: ArbeidssokerperiodeRepository

    @Autowired
    private lateinit var sykepengesoknadConsumer: Consumer<String, String>

    @BeforeAll
    fun subscribeToTopics() {
        sykepengesoknadConsumer.subscribeToTopics(SYKEPENGESOKNAD_TOPIC)
    }

    @BeforeAll
    fun slettFraDatabase() {
        arbeidssokerperiodeRepository.deleteAll()
    }

    @Test
    fun `Mottar SykepengesoknadDTO og lagrer Arbeidssokerperiode`() {
        val key = UUID.randomUUID().toString()
        val soknad = lagFremtidigFriskTilArbeidSoknad()

        kafkaProducer.send(ProducerRecord(SYKEPENGESOKNAD_TOPIC, key, soknad.serialisertTilString())).get()

        sykepengesoknadConsumer.waitForRecords(1).first().also {
            it.key() `should be equal to` key

            it.value().tilSykepengesoknadDTO().also {
                it.type `should be equal to` SoknadstypeDTO.FRISKMELDT_TIL_ARBEIDSFORMIDLING
                it.id `should be equal to` soknad.id
                it.status `should be equal to` SoknadsstatusDTO.FREMTIDIG
                it.fnr `should be equal to` FNR
            }
        }

        arbeidssokerperiodeRepository.findByVedtaksperiodeId(VEDTAKSPERIODE_ID)!!.also {
            it.fnr `should be equal to` FNR
            it.vedtaksperiodeId `should be equal to` VEDTAKSPERIODE_ID
            it.id `should not be equal to` null
            it.opprettet `should not be equal to` null
        }
    }
}
