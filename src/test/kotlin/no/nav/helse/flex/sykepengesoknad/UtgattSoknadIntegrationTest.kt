package no.nav.helse.flex.sykepengesoknad

import no.nav.helse.flex.FNR
import no.nav.helse.flex.FellesTestOppsett
import no.nav.helse.flex.VEDTAKSPERIODE_ID
import no.nav.helse.flex.arbeidssokerperiode.Arbeidssokerperiode
import no.nav.helse.flex.arbeidssokerperiode.AvsluttetAarsak
import no.nav.helse.flex.lagSoknad
import no.nav.helse.flex.`should be within seconds of`
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadsstatusDTO
import no.nav.paw.bekreftelse.paavegneav.v1.vo.Stopp
import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.`should not be equal to`
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.util.*

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class UtgattSoknadIntegrationTest : FellesTestOppsett() {
    @AfterEach
    fun resetMellomTester() {
        slettFraDatabase()
    }

    @AfterAll
    fun verifiserAtTopicErTomt() {
        paaVegneAvConsumer.fetchRecords().size `should be equal to` 0
    }

    @Test
    fun `Søknad med status UTGAATT sender PaaVegneAvStopp og lagrer UTGAATT som årsak`() {
        val lagretPeriode = lagreArbeidssokerperiode()

        lagSoknad(status = SoknadsstatusDTO.UTGAATT).also { sykepengesoknadService.behandleSoknad(it) }

        arbeidssokerperiodeRepository.findById(lagretPeriode.id!!).get().also {
            it.sendtAvsluttet!! `should be within seconds of` (1 to Instant.now())
            it.avsluttetAarsak `should be equal to` AvsluttetAarsak.UTGAATT
        }

        paaVegneAvConsumer.waitForRecords(1).single().also { consumerRecord ->
            consumerRecord.key() `should be equal to` lagretPeriode.kafkaRecordKey
            consumerRecord.value().also {
                it.periodeId.toString() `should be equal to` lagretPeriode.arbeidssokerperiodeId
                (it.handling as Stopp) `should not be equal to` null
            }
        }
    }

    @Test
    fun `Flere søknader med status UTGAATT fra samme bruker sender PaaVegneAvStopp én gang`() {
        val lagretPeriode = lagreArbeidssokerperiode()

        lagSoknad(status = SoknadsstatusDTO.UTGAATT).also { sykepengesoknadService.behandleSoknad(it) }
        lagSoknad(status = SoknadsstatusDTO.UTGAATT).also { sykepengesoknadService.behandleSoknad(it) }

        arbeidssokerperiodeRepository.findById(lagretPeriode.id!!).get().also {
            it.sendtAvsluttet!! `should be within seconds of` (1 to Instant.now())
            it.avsluttetAarsak `should be equal to` AvsluttetAarsak.UTGAATT
        }

        paaVegneAvConsumer.fetchRecords(Duration.ofSeconds(1)).also { records -> records.size `should be equal to` 1 }
    }

    @Test
    fun `Søknad med status UTGAATT sender ikke PaaVegneAvStopp hvis perioden allerede er avsluttet`() {
        val lagretPeriode =
            lagreArbeidssokerperiode().also {
                arbeidssokerperiodeRepository.save(
                    it.copy(
                        sendtAvsluttet = Instant.now(),
                        avsluttetAarsak = AvsluttetAarsak.BRUKER,
                    ),
                )
            }

        val utgattSoknad = lagSoknad(status = SoknadsstatusDTO.UTGAATT)
        sykepengesoknadService.behandleSoknad(utgattSoknad)

        arbeidssokerperiodeRepository.findById(lagretPeriode.id!!).get().also {
            it.avsluttetAarsak `should be equal to` AvsluttetAarsak.BRUKER
        }
    }

    private fun lagreArbeidssokerperiode(): Arbeidssokerperiode =
        arbeidssokerperiodeRepository.save(
            Arbeidssokerperiode(
                fnr = FNR,
                vedtaksperiodeId = VEDTAKSPERIODE_ID,
                vedtaksperiodeFom = LocalDate.now(),
                vedtaksperiodeTom = LocalDate.now().plusDays(14),
                opprettet = Instant.now(),
                kafkaRecordKey = 1000L,
                arbeidssokerperiodeId = UUID.randomUUID().toString(),
                sendtPaaVegneAv = Instant.now(),
            ),
        )
}
