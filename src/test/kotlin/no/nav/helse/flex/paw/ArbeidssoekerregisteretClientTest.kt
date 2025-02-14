package no.nav.helse.flex.paw

import no.nav.helse.flex.FellesTestOppsett
import org.springframework.beans.factory.annotation.Autowired

class ArbeidssoekerregisteretClientTest : FellesTestOppsett() {
    @Autowired
    private lateinit var arbeidssoekerregisteretClient: ArbeidssoekerregisteretClient
}
