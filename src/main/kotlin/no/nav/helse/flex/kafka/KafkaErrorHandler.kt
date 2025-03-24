package no.nav.helse.flex.kafka

import org.springframework.kafka.listener.DefaultErrorHandler
import org.springframework.stereotype.Component
import org.springframework.util.backoff.ExponentialBackOff

@Component
class KafkaErrorHandler :
    DefaultErrorHandler(
        ExponentialBackOff(1000L, 1.5).apply {
            // 8 minutter, som er mindre enn max.poll.interval.ms på 10 minutter.
            maxInterval = 60_000L * 8
        },
    )
