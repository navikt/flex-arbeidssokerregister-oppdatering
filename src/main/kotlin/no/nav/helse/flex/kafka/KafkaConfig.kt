package no.nav.helse.flex.kafka

import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig.ACKS_CONFIG
import org.apache.kafka.clients.producer.ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG
import org.apache.kafka.clients.producer.ProducerConfig.RETRIES_CONFIG
import org.apache.kafka.clients.producer.ProducerConfig.RETRY_BACKOFF_MS_CONFIG
import org.apache.kafka.clients.producer.ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG
import org.apache.kafka.common.config.SslConfigs
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory
import org.springframework.kafka.core.DefaultKafkaConsumerFactory
import org.springframework.kafka.listener.ContainerProperties.AckMode

const val JAVA_KEYSTORE = "JKS"
const val PKCS12 = "PKCS12"

@Configuration
class KafkaConfig(
    @Value("\${KAFKA_BROKERS}") private val kafkaBrokers: String,
    @Value("\${KAFKA_TRUSTSTORE_PATH}") private val kafkaTruststorePath: String,
    @Value("\${aiven-kafka.auto-offset-reset}") private val kafkaAutoOffsetReset: String,
    @Value("\${aiven-kafka.security-protocol}") private val kafkaSecurityProtocol: String,
    @Value("\${KAFKA_CREDSTORE_PASSWORD}") private val kafkaCredstorePassword: String,
    @Value("\${KAFKA_KEYSTORE_PATH}") private val kafkaKeystorePath: String,
) {
    val brokerConfig =
        mapOf(
            BOOTSTRAP_SERVERS_CONFIG to kafkaBrokers,
        )

    val securityConfig =
        mapOf(
            CommonClientConfigs.SECURITY_PROTOCOL_CONFIG to kafkaSecurityProtocol,
            // Disables server host name verification.
            SslConfigs.SSL_ENDPOINT_IDENTIFICATION_ALGORITHM_CONFIG to "",
            SslConfigs.SSL_TRUSTSTORE_TYPE_CONFIG to JAVA_KEYSTORE,
            SslConfigs.SSL_KEYSTORE_TYPE_CONFIG to PKCS12,
            SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG to kafkaTruststorePath,
            SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG to kafkaCredstorePassword,
            SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG to kafkaKeystorePath,
            SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG to kafkaCredstorePassword,
            SslConfigs.SSL_KEY_PASSWORD_CONFIG to kafkaCredstorePassword,
        )

    @Bean
    fun kafkaListenerContainerFactory(kafkaErrorHandler: KafkaErrorHandler) =
        ConcurrentKafkaListenerContainerFactory<String, String>().also {
            val consumerConfig =
                mapOf(
                    ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
                    ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
                    ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to kafkaAutoOffsetReset,
                    ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG to "600000",
                    ConsumerConfig.MAX_POLL_RECORDS_CONFIG to "100",
                    ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG to false,
                )
            it.consumerFactory = DefaultKafkaConsumerFactory(consumerConfig + brokerConfig + securityConfig)
            it.setCommonErrorHandler(kafkaErrorHandler)
            it.containerProperties.ackMode = AckMode.MANUAL_IMMEDIATE
        }

    @Bean
    fun kafkaProducer(): KafkaProducer<String, String> {
        val configs =
            mapOf(
                KEY_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
                VALUE_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
                ACKS_CONFIG to "all",
                RETRIES_CONFIG to 10,
                RETRY_BACKOFF_MS_CONFIG to 100,
            ) + brokerConfig + securityConfig
        return KafkaProducer<String, String>(configs)
    }
}
