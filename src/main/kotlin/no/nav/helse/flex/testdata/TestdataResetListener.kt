package no.nav.helse.flex.testdata

import io.opentelemetry.instrumentation.annotations.WithSpan
import no.nav.helse.flex.arbeidssokerperiode.ArbeidssokerperiodeRepository
import no.nav.helse.flex.logger
import no.nav.helse.flex.sykepengesoknad.PeriodebekreftelseRepository
import no.nav.helse.flex.sykepengesoknad.VedtaksperiodeExceptionRepository
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
    private val vedtaksperiodeExceptionRepository: VedtaksperiodeExceptionRepository,
) {
    private val log = logger()

    @WithSpan
    @KafkaListener(
        topics = [TESTDATA_RESET_TOPIC],
        id = "flex-arbeidssokerregister-oppdatering-testdatareset-v1",
        containerFactory = "kafkaListenerContainerFactory",
        properties = ["auto.offset.reset = latest"],
    )
    fun listen(
        cr: ConsumerRecord<String, String>,
        acknowledgment: Acknowledgment,
    ) {
        val fnr = cr.value()
        vedtaksperiodeExceptionRepository.deleteByFnr(fnr)
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
