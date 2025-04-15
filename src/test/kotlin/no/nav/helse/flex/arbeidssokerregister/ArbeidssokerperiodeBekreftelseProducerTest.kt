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
        val bekreftelseMelding =
            BekreftelseMelding(
                kafkaKey = -3771L,
                arbeidssokerperiodeId = UUID.randomUUID().toString(),
                arbeidssokerregisterPeriodeId = UUID.randomUUID().toString(),
                fnr = FNR,
                periodeStart = Instant.now(),
                periodeSlutt = Instant.now(),
                inntektUnderveis = true,
                fortsattArbeidssoker = true,
            )

        bekreftelseProducer.send(bekreftelseMelding)

        bekreftelseConsumer.waitForRecords(1).single().also {
            it.key() `should be equal to` bekreftelseMelding.kafkaKey
            it.value().periodeId `should be equal to` UUID.fromString(bekreftelseMelding.arbeidssokerregisterPeriodeId)
        }
    }
}
