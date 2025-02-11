package no.nav.helse.flex.kafka

import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.core.DefaultKafkaConsumerFactory
import org.springframework.kafka.core.DefaultKafkaProducerFactory

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
    fun testdataResetTestConsumer(): Consumer<String, String> = lagConsumer("testdatareset-consumer")

    @Bean
    fun sykepengesoknadTestConsumer(): Consumer<String, String> = lagConsumer("sykepengesoknad-consumer")

    @Bean
    fun arbeidssokerRegisterStoppTestConsumer(): Consumer<String, String> = lagConsumer("arbeidssokerstopp-consumer")

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
