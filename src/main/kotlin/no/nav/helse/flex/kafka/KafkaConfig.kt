package no.nav.helse.flex.kafka

import io.confluent.kafka.schemaregistry.client.SchemaRegistryClientConfig
import io.confluent.kafka.serializers.KafkaAvroDeserializer
import io.confluent.kafka.serializers.KafkaAvroDeserializerConfig
import io.confluent.kafka.serializers.KafkaAvroSerializer
import io.confluent.kafka.serializers.KafkaAvroSerializerConfig
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerConfig.ACKS_CONFIG
import org.apache.kafka.clients.producer.ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG
import org.apache.kafka.clients.producer.ProducerConfig.RETRIES_CONFIG
import org.apache.kafka.clients.producer.ProducerConfig.RETRY_BACKOFF_MS_CONFIG
import org.apache.kafka.clients.producer.ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG
import org.apache.kafka.common.config.SslConfigs
import org.apache.kafka.common.serialization.LongDeserializer
import org.apache.kafka.common.serialization.LongSerializer
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory
import org.springframework.kafka.core.DefaultKafkaConsumerFactory
import org.springframework.kafka.core.DefaultKafkaProducerFactory
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
    @Value("\${KAFKA_SCHEMA_REGISTRY}") private val kafkaSchemaRegistryUrl: String,
    @Value("\${KAFKA_SCHEMA_REGISTRY_USER}") private val schemaRegistryUsername: String,
    @Value("\${KAFKA_SCHEMA_REGISTRY_PASSWORD}") private val schemaRegistryPassword: String,
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

    val consumerConfig =
        mapOf(
            ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to kafkaAutoOffsetReset,
            ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG to "600000",
            ConsumerConfig.MAX_POLL_RECORDS_CONFIG to "100",
            ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG to false,
        )

    val producerConfig =
        mapOf(
            ACKS_CONFIG to "all",
            RETRIES_CONFIG to 10,
            RETRY_BACKOFF_MS_CONFIG to 100,
        )

    val schemaRegistryConfig =
        mapOf(
            KafkaAvroSerializerConfig.SCHEMA_REGISTRY_URL_CONFIG to kafkaSchemaRegistryUrl,
            SchemaRegistryClientConfig.BASIC_AUTH_CREDENTIALS_SOURCE to "USER_INFO",
            SchemaRegistryClientConfig.USER_INFO_CONFIG to "$schemaRegistryUsername:$schemaRegistryPassword",
        )

    @Bean
    fun kafkaListenerContainerFactory(kafkaErrorHandler: KafkaErrorHandler) =
        ConcurrentKafkaListenerContainerFactory<String, String>().also {
            val consumerConfig =
                mapOf(
                    ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
                    ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
                ) + consumerConfig + brokerConfig + securityConfig

            it.consumerFactory = DefaultKafkaConsumerFactory(consumerConfig)
            it.setCommonErrorHandler(kafkaErrorHandler)
            it.containerProperties.ackMode = AckMode.MANUAL_IMMEDIATE
        }

    @Bean
    fun kafkaProducer(): Producer<String, String> =
        DefaultKafkaProducerFactory<String, String>(
            mapOf(
                KEY_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
                VALUE_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
            ) + producerConfig + brokerConfig + securityConfig,
        ).createProducer()

    // Navngir for å hjelpe Spring med mathcing siden vi bruker Generics.
    @Bean("avroKafkaListenerContainerFactory")
    @ConditionalOnMissingBean(name = ["avroKafkaListenerContainerFactory"])
    fun <T> avroKafkaListenerContainerFactory(kafkaErrorHandler: KafkaErrorHandler) =
        ConcurrentKafkaListenerContainerFactory<Long, T>().also {
            val consumerConfig =
                mapOf(
                    ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to LongDeserializer::class.java,
                    ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to KafkaAvroDeserializer::class.java,
                    KafkaAvroDeserializerConfig.SPECIFIC_AVRO_READER_CONFIG to true,
                ) + consumerConfig + brokerConfig + securityConfig + schemaRegistryConfig

            it.consumerFactory = DefaultKafkaConsumerFactory(consumerConfig)
            it.setCommonErrorHandler(kafkaErrorHandler)
            it.containerProperties.ackMode = AckMode.MANUAL_IMMEDIATE
        }

    // Navngir for å hjelpe Spring med mathcing siden vi bruker generiske parametere.
    @Bean("avroKafkaProducer")
    @ConditionalOnMissingBean(name = ["avroKafkaProducer"])
    fun <T> avroKafkaProducer(): Producer<Long, T> =
        DefaultKafkaProducerFactory<Long, T>(
            mapOf(
                KEY_SERIALIZER_CLASS_CONFIG to LongSerializer::class.java,
                VALUE_SERIALIZER_CLASS_CONFIG to KafkaAvroSerializer::class.java,
            ) + producerConfig + brokerConfig + securityConfig + schemaRegistryConfig,
        ).createProducer()
}
