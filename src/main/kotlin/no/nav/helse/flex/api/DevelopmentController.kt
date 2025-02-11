package no.nav.helse.flex.api

import no.nav.helse.flex.arbeidssoker.ArbeidssokerRegisterStopp
import no.nav.helse.flex.arbeidssoker.ArbeidssokerRegisterStoppProducer
import no.nav.helse.flex.logger
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseBody
import java.util.*

@Profile("testdatareset")
@Controller
@RequestMapping("/api/v1/development")
class DevelopmentController(
    private val arbeidssokerRegisterStoppProducer: ArbeidssokerRegisterStoppProducer,
) {
    private val log = logger()

    @GetMapping("/verifiser")
    @ResponseBody
    fun developmentResponse(): DevelopmentResponse = DevelopmentResponse("Verifisert.")

    @PostMapping("/stopp")
    @ResponseBody
    fun sendArbeidssokerRegisterStopp(fnr: String) {
        val id = UUID.randomUUID().toString()
        val arbeidssokerRegisterStopp = ArbeidssokerRegisterStopp(id = id, fnr = fnr)
        arbeidssokerRegisterStoppProducer.send(arbeidssokerRegisterStopp)
        log.info("Sendt ArbeidssokerRegisterStopp med id: $id.")
    }
}

data class DevelopmentResponse(
    val message: String,
)
