package no.nav.helse.flex.sykepengesoknad

import io.opentelemetry.instrumentation.annotations.WithSpan
import no.nav.helse.flex.EnvironmentToggles
import no.nav.helse.flex.arbeidssokerperiode.ArbeidssokerperiodeRepository
import no.nav.helse.flex.arbeidssokerregister.ArbeidssokerperiodeRequest
import no.nav.helse.flex.arbeidssokerregister.ArbeidssokerregisterClient
import no.nav.helse.flex.logger
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadsstatusDTO
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadstypeDTO
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.listener.ConsumerSeekAware
import org.springframework.kafka.listener.ConsumerSeekAware.ConsumerSeekCallback
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component
import java.time.LocalDate
import kotlin.collections.contains

@Component
class SykepengesoknadVedtakListener(
    private val environmentToggles: EnvironmentToggles,
    private val arbeidssokerperiodeRepository: ArbeidssokerperiodeRepository,
    private val periodebekreftelseRepository: PeriodebekreftelseRepository,
    private val arbeidssokerregisterClient: ArbeidssokerregisterClient,
) : ConsumerSeekAware {
    private val log = logger()

    private val behandlet = mutableSetOf<String>()

    @WithSpan
    @KafkaListener(
        topics = [SYKEPENGESOKNAD_TOPIC],
        id = "flex-arbeidssokerregister-oppdatering-vedtak-debug-v2",
        containerFactory = "kafkaListenerContainerFactory",
        properties = ["auto.offset.reset=earliest"],
        concurrency = "6",
    )
    fun listen(
        cr: ConsumerRecord<String, String>,
        acknowledgment: Acknowledgment,
    ) {
        if (!environmentToggles.erProduksjon()) {
            acknowledgment.acknowledge()
            return
        }

        cr.value().tilSykepengesoknadDTO().also {
            if (behandlet.contains(it.id)) {
                return@also
            }

            if (it.type != SoknadstypeDTO.FRISKMELDT_TIL_ARBEIDSFORMIDLING) {
                return@also
            }

            if (it.status != SoknadsstatusDTO.FREMTIDIG) {
                return@also
            }

            if (
                it.friskTilArbeidVedtakId in
                listOf(
                    "4fe42342-7102-44d8-acd9-dc6a4f226f5a",
                    "e4504199-f052-469a-9e0d-bffd1bad6bef",
                    "688142df-92d9-4f44-b176-fd74d0c5da1d",
                    "bedb05d3-a2ff-4ee3-8525-44965b21442c",
                    "5abde058-fc10-470f-a336-0daccd7ed733",
                    "fe691fa4-245f-4bd9-abfd-1222b9353627",
                )
            ) {
                return@also
            }

            // Sjekker om det finnes en eksisterende vedtaksperiode for samme vedtak, som har avsluttetMottatt før
            // ny FREMTIDIG søknad søknaden ble opprettet og sjekk om den har periodebekreftelser.
            arbeidssokerperiodeRepository.findByVedtaksperiodeId(it.friskTilArbeidVedtakId!!)?.let {
                if (it.avsluttetMottatt != null && it.avsluttetMottatt.isBefore(it.opprettet)) {
                    val periodebekreftelser = periodebekreftelseRepository.findByArbeidssokerperiodeId(it.id!!)

                    var registert =
                        arbeidssokerregisterClient.hentSisteArbeidssokerperiode(ArbeidssokerperiodeRequest(it.fnr))
                    val aktivRegistrering = registert.isNotEmpty() && registert.single().avsluttet == null

                    log.info(
                        "Det eksisterer en Arbeidssøkerperiode med vedtaksperiodeId: ${it.vedtaksperiodeId} for " +
                            "søknad: ${it.id}. Den har avsluttetMottatt: ${it.avsluttetMottatt} " +
                            "som er før søknaden ble opprettet: ${it.opprettet}. " +
                            "Den har ${periodebekreftelser.size} periodebekreftelser. " +
                            "Bruker er registrert i arbeidssøkerregisteret: $aktivRegistrering.",
                    )

                    // TODO: Slett eksisterende og behandle søknad på vanlig måte.
                    // TODO: Hva gjør vi hvis perioden allerede har periodebekreftelser.

                    // Vi trenger bare å prosessere en av de FREMTIDIGE søknadene som oppretted for en vedtaksperiode.
                    behandlet.add(it.fnr)
                }
            }
        }
        acknowledgment.acknowledge()
    }

    override fun onPartitionsAssigned(
        assignments: Map<org.apache.kafka.common.TopicPartition?, Long?>,
        callback: ConsumerSeekCallback,
    ) {
        val startTimestamp = LocalDate.of(2025, 3, 20).toInstantAtStartOfDay().toEpochMilli()

        assignments.keys.filterNotNull().forEach { topicPartition ->
            callback.seekToTimestamp(topicPartition.topic(), topicPartition.partition(), startTimestamp)
        }
    }
}
