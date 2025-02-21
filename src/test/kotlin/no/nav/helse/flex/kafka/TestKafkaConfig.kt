package no.nav.helse.flex.kafka

import io.confluent.kafka.schemaregistry.avro.AvroSchema
import io.confluent.kafka.schemaregistry.client.MockSchemaRegistryClient
import io.confluent.kafka.serializers.KafkaAvroDeserializer
import io.confluent.kafka.serializers.KafkaAvroDeserializerConfig
import io.confluent.kafka.serializers.KafkaAvroSerializer
import io.confluent.kafka.serializers.KafkaAvroSerializerConfig
import no.nav.helse.flex.arbeidssokerregister.ARBEIDSSOKERPERIODE_BEKREFTELSE_TOPIC
import no.nav.helse.flex.arbeidssokerregister.ARBEIDSSOKERPERIODE_PAA_VEGNE_AV_TOPIC
import no.nav.helse.flex.arbeidssokerregister.ARBEIDSSOKERPERIODE_TOPIC
import no.nav.helse.flex.sykepengesoknad.ARBEIDSSOKERPERIODE_STOPP_TOPIC
import no.nav.helse.flex.sykepengesoknad.SYKEPENGESOKNAD_TOPIC
import no.nav.helse.flex.testdata.TESTDATA_RESET_TOPIC
import no.nav.paw.arbeidssokerregisteret.api.v1.Periode
import org.apache.kafka.clients.admin.NewTopic
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.common.config.SaslConfigs
import org.apache.kafka.common.serialization.Deserializer
import org.apache.kafka.common.serialization.LongDeserializer
import org.apache.kafka.common.serialization.LongSerializer
import org.apache.kafka.common.serialization.Serializer
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory
import org.springframework.kafka.config.TopicBuilder
import org.springframework.kafka.core.DefaultKafkaConsumerFactory
import org.springframework.kafka.core.DefaultKafkaProducerFactory
import org.springframework.kafka.core.KafkaAdmin
import org.springframework.kafka.listener.ContainerProperties.AckMode

@Configuration
@Suppress("UNCHECKED_CAST")
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
    fun lagArbeidsokerperiodeStoppTopic(): NewTopic =
        TopicBuilder
            .name(ARBEIDSSOKERPERIODE_STOPP_TOPIC)
            .build()

    @Bean
    fun lagArbeidsokerperiodeBekrefelseTopic(): NewTopic =
        TopicBuilder
            .name(ARBEIDSSOKERPERIODE_BEKREFTELSE_TOPIC)
            .build()

    @Bean
    fun lagArbeidsokerperiodePaaVegneTopic(): NewTopic =
        TopicBuilder
            .name(ARBEIDSSOKERPERIODE_PAA_VEGNE_AV_TOPIC)
            .build()

    @Bean
    fun mockSchemaRegistryClient(): MockSchemaRegistryClient =
        MockSchemaRegistryClient().also {
            it.register(
                "$ARBEIDSSOKERPERIODE_TOPIC-value",
                AvroSchema(Periode.`SCHEMA$`),
            )
        }

    @Bean
    fun testdataResetTestConsumer() = lagConsumer("testdatareset-consumer")

    @Bean
    fun sykepengesoknadTestConsumer() = lagConsumer("sykepengesoknad-consumer")

    @Bean
    fun arbeidssokerperiodeStoppTestConsumer() = lagConsumer("arbeidssokerperiodestopp-consumer")

    @Bean
    fun <T> arbeidssokerperiodeTestConsumer(mockSchemaRegistryClient: MockSchemaRegistryClient): Consumer<Long, T> =
        lagAvroConsumer(mockSchemaRegistryClient, "arbeidssokerperiode-consumer")

    @Bean
    fun <T> bekreftelseTestConsumer(mockSchemaRegistryClient: MockSchemaRegistryClient): Consumer<Long, T> =
        lagAvroConsumer(mockSchemaRegistryClient, "bekreftelse-consumer")

    @Bean
    fun <T> paaVegneAvTestConsumer(mockSchemaRegistryClient: MockSchemaRegistryClient): Consumer<Long, T> =
        lagAvroConsumer(mockSchemaRegistryClient, "paavegneav-consumer")

    @Bean
    fun testProducer(): Producer<String, String> =
        DefaultKafkaProducerFactory<String, String>(
            kafkaConfig.producerConfig + kafkaConfig.brokerConfig,
            StringSerializer(),
            StringSerializer(),
        ).createProducer()

    // Brukes både i tester og som Producer i klassene som testes sånn at MockSchemaRegistryClient brukes.
    @Bean("avroKafkaProducer")
    fun <T> avroKafkaProducer(mockSchemaRegistryClient: MockSchemaRegistryClient): Producer<Long, T> {
        @Suppress("UNCHECKED_CAST")
        return DefaultKafkaProducerFactory(
            mapOf(
                KafkaAvroSerializerConfig.SCHEMA_REGISTRY_URL_CONFIG to "http://ikke.i.bruk.nav",
                SaslConfigs.SASL_MECHANISM to "PLAIN",
            ) + kafkaConfig.producerConfig + kafkaConfig.brokerConfig,
            LongSerializer(),
            KafkaAvroSerializer(mockSchemaRegistryClient) as Serializer<T>,
        ).createProducer()
    }

    // Ersatter ConcurrentKafkaListenerContainerFactory i klassene som testes sånn at MockSchemaRegistryClient brukes.
    @Bean("avroKafkaListenerContainerFactory")
    fun <T> avroKafkaListenerContainerFactory(
        kafkaErrorHandler: KafkaErrorHandler,
        mockSchemaRegistryClient: MockSchemaRegistryClient,
    ) = ConcurrentKafkaListenerContainerFactory<Long, T>().also {
        @Suppress("UNCHECKED_CAST")
        it.consumerFactory =
            DefaultKafkaConsumerFactory(
                lagAvroDeserializerConfig(),
                LongDeserializer(),
                KafkaAvroDeserializer(mockSchemaRegistryClient) as Deserializer<T>,
            )
        it.setCommonErrorHandler(kafkaErrorHandler)
        it.containerProperties.ackMode = AckMode.MANUAL_IMMEDIATE
    }

    private fun lagConsumer(groupId: String): Consumer<String, String> =
        DefaultKafkaConsumerFactory(
            mapOf(
                ConsumerConfig.GROUP_ID_CONFIG to groupId,
            ) + consumerConfig + kafkaConfig.brokerConfig,
            StringDeserializer(),
            StringDeserializer(),
        ).createConsumer()

    private fun <T> lagAvroConsumer(
        mockSchemaRegistryClient: MockSchemaRegistryClient,
        groupId: String,
    ) = lagAvroConsumerFactory<T>(mockSchemaRegistryClient, lagAvroDeserializerConfig(groupId)).createConsumer()

    private fun <T> lagAvroConsumerFactory(
        mockSchemaRegistryClient: MockSchemaRegistryClient,
        avroDeserializerConfig: Map<String, Any>,
    ) = DefaultKafkaConsumerFactory(
        avroDeserializerConfig,
        LongDeserializer(),
        KafkaAvroDeserializer(mockSchemaRegistryClient) as Deserializer<T>,
    )

    private fun lagAvroDeserializerConfig(groupId: String? = null): Map<String, Any> {
        val groupIdConfig = groupId?.let { mapOf(ConsumerConfig.GROUP_ID_CONFIG to it) } ?: emptyMap()
        return mapOf(
            KafkaAvroDeserializerConfig.SCHEMA_REGISTRY_URL_CONFIG to "http://ikke.i.bruk.nav",
            KafkaAvroDeserializerConfig.SPECIFIC_AVRO_READER_CONFIG to true,
        ) + consumerConfig + kafkaConfig.brokerConfig + groupIdConfig
    }
}
