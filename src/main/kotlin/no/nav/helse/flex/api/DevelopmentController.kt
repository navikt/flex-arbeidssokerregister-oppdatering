package no.nav.helse.flex.api

import org.springframework.http.MediaType
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ResponseBody

@Controller
class DevelopmentController() {
    @GetMapping("/api/v1/development", produces = [MediaType.APPLICATION_JSON_VALUE])
    @ResponseBody
    fun developmentResponse(): DevelopmentResponse {
        return DevelopmentResponse("Response.")
    }
}

data class DevelopmentResponse(val message: String)
