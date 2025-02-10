package no.nav.helse.flex

import org.junit.jupiter.api.TestInstance
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability
import org.springframework.boot.test.context.SpringBootTest
import org.testcontainers.containers.PostgreSQLContainer

@SpringBootTest(classes = [Application::class])
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@AutoConfigureObservability
abstract class FellesTestOppsett {
    companion object {
        init {
            PostgreSQLContainer16().apply {
                start()
                System.setProperty("spring.datasource.url", "$jdbcUrl&reWriteBatchedInserts=true")
                System.setProperty("spring.datasource.username", username)
                System.setProperty("spring.datasource.password", password)
            }
        }
    }
}

private class PostgreSQLContainer16 : PostgreSQLContainer<PostgreSQLContainer16>("postgres:16-alpine")
