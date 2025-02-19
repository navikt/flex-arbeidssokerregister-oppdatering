package no.nav.helse.flex.kafka

import no.nav.helse.flex.sykepengesoknad.ARBEIDSSOKERREGISTER_PERIODE_STOPP_TOPIC
import no.nav.helse.flex.sykepengesoknad.SYKEPENGESOKNAD_TOPIC
import no.nav.helse.flex.testdata.TESTDATA_RESET_TOPIC
import org.apache.kafka.clients.admin.NewTopic
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.config.TopicBuilder
import org.springframework.kafka.core.DefaultKafkaConsumerFactory
import org.springframework.kafka.core.DefaultKafkaProducerFactory
import org.springframework.kafka.core.KafkaAdmin

@Configuration
class TestKafkaConfig(
    private val kafkaConfig: KafkaConfig,
) {
    private val consumerConfig =
        mapOf(
            ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to "earliest",
            ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG to false,
            ConsumerConfig.MAX_POLL_RECORDS_CONFIG to "1",
        )

    @Bean
    fun createKafkaAdmin(): KafkaAdmin = KafkaAdmin(kafkaConfig.brokerConfig)

    // Fjerner feilmelding UNKNOWN_TOPIC_OR_PARTITION når tester som lytter på Kafka starter før Testcontainers.
    @Bean
    fun lagSykepengesoknadTopic(): NewTopic =
        TopicBuilder
            .name(SYKEPENGESOKNAD_TOPIC)
            .build()

    @Bean
    fun lagTestDataResetTopic(): NewTopic =
        TopicBuilder
            .name(TESTDATA_RESET_TOPIC)
            .build()

    @Bean
    fun lagArbeidssokerregisterStoppTopic(): NewTopic =
        TopicBuilder
            .name(ARBEIDSSOKERREGISTER_PERIODE_STOPP_TOPIC)
            .build()

    @Bean
    fun testdataResetTestConsumer(): Consumer<String, String> = lagConsumer("testdatareset-consumer")

    @Bean
    fun sykepengesoknadTestConsumer(): Consumer<String, String> = lagConsumer("sykepengesoknad-consumer")

    @Bean
    fun arbeidssokerregisterStoppTestConsumer(): Consumer<String, String> = lagConsumer("arbeidssokerregisterstopp-consumer")

    @Bean
    fun testProducer(): Producer<String, String> =
        DefaultKafkaProducerFactory<String, String>(
            mapOf(
                ProducerConfig.ACKS_CONFIG to "all",
                ProducerConfig.RETRIES_CONFIG to 10,
                ProducerConfig.RETRY_BACKOFF_MS_CONFIG to 100,
            ) + kafkaConfig.brokerConfig,
            StringSerializer(),
            StringSerializer(),
        ).createProducer()

    private fun lagConsumer(groupId: String): Consumer<String, String> =
        DefaultKafkaConsumerFactory(
            mapOf(
                ConsumerConfig.GROUP_ID_CONFIG to groupId,
            ) + consumerConfig + kafkaConfig.brokerConfig,
            StringDeserializer(),
            StringDeserializer(),
        ).createConsumer()
}
