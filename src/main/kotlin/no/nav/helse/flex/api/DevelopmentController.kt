package no.nav.helse.flex.api

import no.nav.helse.flex.arbeidssoker.ArbeidssokerRegisterStopp
import no.nav.helse.flex.arbeidssoker.ArbeidssokerRegisterStoppProducer
import no.nav.helse.flex.paw.KafkaKeyGeneratorClient
import no.nav.helse.flex.paw.KafkaKeyGeneratorRequest
import no.nav.security.token.support.core.api.Unprotected
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseBody
import java.util.*

@Profile("testdatareset")
@Unprotected
@Controller
@RequestMapping("/api/v1")
class DevelopmentController(
    private val arbeidssokerRegisterStoppProducer: ArbeidssokerRegisterStoppProducer,
    private val kafkaKeyGeneratorClient: KafkaKeyGeneratorClient,
) {
    @PostMapping("/stopp")
    @ResponseBody
    fun sendtStopp(fnr: String): DevelopmentResponse {
        val id = UUID.randomUUID().toString()
        arbeidssokerRegisterStoppProducer.send(ArbeidssokerRegisterStopp(id = id, fnr = fnr))
        return DevelopmentResponse("id=$id")
    }

    @GetMapping("/key/{fnr}")
    @ResponseBody
    fun hentKafkaKey(
        @PathVariable fnr: String,
    ): DevelopmentResponse {
        kafkaKeyGeneratorClient.hentKafkaKey(KafkaKeyGeneratorRequest(fnr))!!.let {
            return DevelopmentResponse("$it")
        }
    }
}

data class DevelopmentResponse(
    val message: String,
)
