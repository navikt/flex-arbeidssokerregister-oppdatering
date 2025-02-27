package no.nav.helse.flex.arbeidssokerregister

import no.nav.helse.flex.FNR
import no.nav.helse.flex.FellesTestOppsett
import org.amshove.kluent.`should be equal to`
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.*

class ArbeidssokerperiodeBekreftelseProducerTest : FellesTestOppsett() {
    @Test
    fun `Kan serialisere og sende Bekreftelse`() {
        val arbeidssokerperiodeBekreftelse =
            ArbeidssokerperiodeBekreftelse(
                kafkaKey = -3771L,
                periodeId = UUID.randomUUID(),
                fnr = FNR,
                periodeStart = Instant.now(),
                periodeSlutt = Instant.now(),
                inntektUnderveis = true,
                fortsattArbeidssoker = true,
            )

        bekreftelseProducer.send(arbeidssokerperiodeBekreftelse)

        bekreftelseConsumer.waitForRecords(1).single().also {
            it.key() `should be equal to` arbeidssokerperiodeBekreftelse.kafkaKey
            it.value().periodeId `should be equal to` arbeidssokerperiodeBekreftelse.periodeId
        }
    }
}
