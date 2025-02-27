package no.nav.helse.flex

import no.nav.helse.flex.sykepengesoknad.Periode
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadsstatusDTO
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadstypeDTO
import no.nav.helse.flex.sykepengesoknad.kafka.SykepengesoknadDTO
import no.nav.security.token.support.spring.test.EnableMockOAuth2Server
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.QueueDispatcher
import okhttp3.mockwebserver.RecordedRequest
import org.amshove.kluent.should
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.awaitility.Awaitility
import org.junit.jupiter.api.TestInstance
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.kafka.KafkaContainer
import org.testcontainers.utility.DockerImageName
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.util.*
import kotlin.math.abs

const val FNR = "11111111111"
const val VEDTAKSPERIODE_ID = "52198b00-c980-4a68-832f-42b2c21316a2"

@SpringBootTest(classes = [Application::class])
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@AutoConfigureObservability
@EnableMockOAuth2Server
abstract class FellesTestOppsett {
    companion object {
        init {
            PostgreSQLContainer16().apply {
                start()
                System.setProperty("spring.datasource.url", "$jdbcUrl&reWriteBatchedInserts=true")
                System.setProperty("spring.datasource.username", username)
                System.setProperty("spring.datasource.password", password)
            }

            KafkaContainer(DockerImageName.parse("apache/kafka-native:3.8.1")).apply {
                start()
                System.setProperty("KAFKA_BROKERS", bootstrapServers)
            }
        }

        val kafkaKeyGeneratorMockWebServer =
            MockWebServer().apply {
                System.setProperty("KAFKA_KEY_GENERATOR_URL", "http://localhost:$port")
                dispatcher = KafkaKeyGeneratorMockDispatcher
            }

        val arbeidssokerperiodeMockWebServer =
            MockWebServer().apply {
                System.setProperty("ARBEIDSSOEKERREGISTERET_API_URL", "http://localhost:$port")
                dispatcher = ArbeidssokerperiodeMockDispatcher
            }
    }

    fun <K, V> Consumer<K, V>.subscribeToTopics(vararg topics: String) {
        if (this.subscription().isEmpty()) {
            this.subscribe(listOf(*topics))
        }
    }

    fun <K, V> Consumer<K, V>.waitForRecords(
        waitForNumberOfRecords: Int,
        duration: Duration = Duration.ofSeconds(2),
    ): List<ConsumerRecord<K, V>> {
        val fetchedRecords = mutableListOf<ConsumerRecord<K, V>>()
        if (waitForNumberOfRecords == 0) {
            Awaitility.await().during(duration)
        } else {
            Awaitility.await().atMost(duration)
        }.until {
            fetchedRecords.addAll(this.fetchRecords())
            fetchedRecords.size == waitForNumberOfRecords
        }
        return fetchedRecords
    }

    fun <K, V> Consumer<K, V>.fetchRecords(duration: Duration = Duration.ofMillis(500)): List<ConsumerRecord<K, V>> =
        this
            .poll(duration)
            .also {
                this.commitSync()
            }.iterator()
            .asSequence()
            .toList()
}

private class PostgreSQLContainer16 : PostgreSQLContainer<PostgreSQLContainer16>("postgres:16-alpine")

object KafkaKeyGeneratorMockDispatcher : QueueDispatcher() {
    override fun dispatch(request: RecordedRequest): MockResponse {
        if (responseQueue.peek() != null) {
            return withContentTypeApplicationJson { responseQueue.take() }
        }

        return MockResponse().setResponseCode(404)
    }
}

object ArbeidssokerperiodeMockDispatcher : QueueDispatcher() {
    override fun dispatch(request: RecordedRequest): MockResponse {
        if (responseQueue.peek() != null) {
            return withContentTypeApplicationJson { responseQueue.take() }
        }

        return MockResponse().setResponseCode(404)
    }
}

private fun withContentTypeApplicationJson(createMockResponse: () -> MockResponse): MockResponse =
    createMockResponse().addHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)

fun lagSoknad(
    status: SoknadsstatusDTO = SoknadsstatusDTO.FREMTIDIG,
    fortsattArbeidssoker: Boolean? = null,
    inntektUnderveis: Boolean? = null,
): SykepengesoknadDTO =
    SykepengesoknadDTO(
        fnr = FNR,
        id = UUID.randomUUID().toString(),
        type = SoknadstypeDTO.FRISKMELDT_TIL_ARBEIDSFORMIDLING,
        status = status,
        fom = LocalDate.of(2025, 1, 1),
        tom = LocalDate.of(2025, 1, 31),
        friskTilArbeidVedtakPeriode =
            Periode(
                LocalDate.of(2025, 1, 1),
                LocalDate.of(2025, 3, 31),
            ).serialisertTilString(),
        friskTilArbeidVedtakId = VEDTAKSPERIODE_ID,
        fortsattArbeidssoker = fortsattArbeidssoker,
        inntektUnderveis = inntektUnderveis,
    )

infix fun Instant.`should be within seconds of`(pair: Pair<Int, Instant>) = this.shouldBeWithinSecondsOf(pair.first.toInt() to pair.second)

infix fun Instant.shouldBeWithinSecondsOf(pair: Pair<Int, Instant>) {
    val (seconds, other) = pair
    val difference = abs(this.epochSecond - other.epochSecond)
    this.should { difference <= seconds }
}
