package no.nav.helse.flex.testdata

import no.nav.helse.flex.ArbeidssokerperiodeRepository
import no.nav.helse.flex.PeriodebekreftelseRepository
import no.nav.helse.flex.logger
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.context.annotation.Profile
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component

@Component
@Profile("test", "testdatareset")
class TestdataResetListener(
    private val arbeidssokerperiodeRepository: ArbeidssokerperiodeRepository,
    private val periodebekreftelseRepository: PeriodebekreftelseRepository,
) {
    private val log = logger()

    @KafkaListener(
        topics = [TESTDATA_RESET_TOPIC],
        id = "flex-arbeidssokerregister-oppdatering-testdatareset",
        containerFactory = "kafkaListenerContainerFactory",
        properties = ["auto.offset.reset = latest"],
    )
    fun listen(
        cr: ConsumerRecord<String, String>,
        acknowledgment: Acknowledgment,
    ) {
        val fnr = cr.value()
        arbeidssokerperiodeRepository.findByFnr(fnr)?.let {
            it.forEach {
                val antall = periodebekreftelseRepository.deleteByArbeidssokerperiodeId(it.id!!)
                arbeidssokerperiodeRepository.delete(it)
                log.info("Slettet arbeidssokerperiode: ${it.id} og $antall tilhørerende periodebekreftelser.")
            }
        }
        acknowledgment.acknowledge()
    }
}

const val TESTDATA_RESET_TOPIC = "flex.testdata-reset"
