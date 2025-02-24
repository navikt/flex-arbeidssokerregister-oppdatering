package no.nav.helse.flex.arbeidssokerperiode

import org.amshove.kluent.`should be equal to`
import org.junit.jupiter.api.Test
import java.time.LocalDate

class BeregnGraceMSTest {
    @Test
    fun `Kalkuler graceMS for periode periode som varer ut januar`() {
        val tom = LocalDate.of(2025, 1, 31)

        beregnGraceMS(tom, 4) `should be equal to` 10368000000
    }

    @Test
    fun `Kalkuler graceMS for periode periode som varer ut mars`() {
        val tom = LocalDate.of(2025, 3, 31)

        beregnGraceMS(tom, 4) `should be equal to` 10540800000
    }

    @Test
    fun `Kalkuler graceMS for periode periode som varer ut januar i et skuddår`() {
        val tom = LocalDate.of(2024, 1, 31)

        beregnGraceMS(tom, 4) `should be equal to` 10454400000
    }

    @Test
    fun `Kalkuler graceMS for periode periode som varer ut april`() {
        val tom = LocalDate.of(2025, 4, 30)
        beregnGraceMS(tom, 4) `should be equal to` 10627200000
    }
}
