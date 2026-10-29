import org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.springframework.boot") version "4.1.2"
    id("io.spring.dependency-management") version "1.1.7"
    id("org.jlleitschuh.gradle.ktlint") version "14.2.0"
    id("avro-codegen")
    kotlin("plugin.spring") version "2.4.10"
    kotlin("jvm") version "2.4.10"
}

group = "no.nav.helse.flex"
version = "1.0.0"
description = "flex-arbeidssokerregister-oppdatering"
java.sourceCompatibility = JavaVersion.VERSION_25

repositories {
    mavenCentral()
    maven {
        url = uri("https://github-package-registry-mirror.gc.nav.no/cached/maven-release")
    }
    maven {
        url = uri("https://packages.confluent.io/maven/")
    }
}

val tokenSupportVersion = "6.0.12"
val mockWebServerVersion = "5.5.0"
val testContainersVersion = "2.0.5"
val logstashLogbackEncoderVersion = "9.0"
val kotestVersion = "6.2.6"
val confluentVersion = "8.2.1"
val avroVersion = "1.12.2"
val sykepengesoknadKafkaVersion = "2026.10.29-06.32-9ce6afeb"
val arbeidssokerregisteretSchemaVersion = "1.25.03.10.2-1"
val bekreftelsesmeldingSchemaVersion = "1.26.05.04.35-1"
val bekreftelsePaaVegneAvSchemaVersion = "1.26.05.04.35-1"
val opentelemetryApiVersion = "1.67.0"
val opentelemetryInstrumentationVersion = "2.32.0"

dependencies {
    schema("no.nav.paw.arbeidssokerregisteret.api:main-avro-schema:$arbeidssokerregisteretSchemaVersion")
    schema("no.nav.paw.arbeidssokerregisteret.api:bekreftelsesmelding-schema:$bekreftelsesmeldingSchemaVersion")
    schema("no.nav.paw.arbeidssokerregisteret.api:bekreftelse-paavegneav-schema:$bekreftelsePaaVegneAvSchemaVersion")
    avroTools("org.apache.avro:avro-tools:$avroVersion")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-kafka")
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.flywaydb:flyway-database-postgresql")
    implementation("org.postgresql:postgresql")
    implementation("org.apache.httpcomponents.client5:httpclient5")
    implementation("io.micrometer:micrometer-registry-prometheus")
    implementation("org.hibernate.validator:hibernate-validator")
    implementation("tools.jackson.module:jackson-module-kotlin")
    implementation("no.nav.security:token-validation-spring:$tokenSupportVersion")
    implementation("no.nav.security:token-client-spring:$tokenSupportVersion")
    implementation("net.logstash.logback:logstash-logback-encoder:$logstashLogbackEncoderVersion")
    implementation("no.nav.helse.flex:sykepengesoknad-kafka:$sykepengesoknadKafkaVersion")
    implementation("org.apache.avro:avro:$avroVersion")
    implementation("io.confluent:kafka-connect-avro-converter:$confluentVersion")
    implementation("io.confluent:kafka-schema-registry-client:$confluentVersion")
    implementation("io.opentelemetry:opentelemetry-api:$opentelemetryApiVersion")
    implementation("io.opentelemetry.instrumentation:opentelemetry-instrumentation-annotations:$opentelemetryInstrumentationVersion")

    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.boot:spring-boot-micrometer-metrics-test")
    testImplementation("org.springframework.boot:spring-boot-micrometer-tracing-test")
    testImplementation("com.squareup.okhttp3:mockwebserver3:$mockWebServerVersion")
    testImplementation("org.testcontainers:testcontainers:$testContainersVersion")
    testImplementation("org.testcontainers:testcontainers-postgresql:$testContainersVersion")
    testImplementation("org.testcontainers:testcontainers-kafka:$testContainersVersion")
    testImplementation("no.nav.security:token-validation-spring-test:$tokenSupportVersion")
    testImplementation("io.kotest:kotest-assertions-core:$kotestVersion")
    testImplementation("org.awaitility:awaitility")
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_25)
        freeCompilerArgs.add("-Xjsr305=strict")
        if (System.getenv("CI") == "true") {
            allWarningsAsErrors.set(true)
        }
    }
}

tasks {
    test {
        useJUnitPlatform()
        jvmArgs("-XX:+EnableDynamicAgentLoading")
        testLogging {
            events("PASSED", "FAILED", "SKIPPED")
            exceptionFormat = FULL
        }
        failFast = false
    }
}

tasks {
    bootJar {
        archiveFileName = "app.jar"
    }
}
