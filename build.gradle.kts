import org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.springframework.boot") version "3.4.3"
    id("io.spring.dependency-management") version "1.1.7"
    id("org.jlleitschuh.gradle.ktlint") version "12.2.0"
    id("com.github.davidmc24.gradle.plugin.avro") version "1.9.1"
    kotlin("plugin.spring") version "2.1.10"
    kotlin("jvm") version "2.1.10"
}

group = "no.nav.helse.flex"
version = "1.0.0"
description = "flex-arbeidssokerregister-oppdatering"
java.sourceCompatibility = JavaVersion.VERSION_21

repositories {
    mavenCentral()
    maven {
        url = uri("https://github-package-registry-mirror.gc.nav.no/cached/maven-release")
    }
    maven {
        url = uri("https://packages.confluent.io/maven/")
    }
}

val schema: Configuration by configurations.creating {
    isTransitive = false
}

ext["okhttp3"] = "4.12" // Token-support tester trenger MockWebServer.

val tokenSupportVersion = "5.0.19"
val testContainersVersion = "1.20.6"
val logstashLogbackEncoderVersion = "8.0"
val kluentVersion = "1.73"
val confluentVersion = "7.9.0"
val avroVersion = "1.12.0"
val sykepengesoknadKafkaVersion = "2025.02.19-16.24-5e00417f"
val arbeidssokerregisteretSchemaVersion = "1.13764081353.1-2"
val bekreftelsesmeldingSchemaVersion = "1.25.02.10.17-1"
val bekreftelsePaaVegneAvSchemaVersion = "1.25.02.10.17-1"

dependencies {
    schema("no.nav.paw.arbeidssokerregisteret.api:main-avro-schema:$arbeidssokerregisteretSchemaVersion")
    schema("no.nav.paw.arbeidssokerregisteret.api:bekreftelsesmelding-schema:$bekreftelsesmeldingSchemaVersion")
    schema("no.nav.paw.arbeidssokerregisteret.api:bekreftelse-paavegneav-schema:$bekreftelsePaaVegneAvSchemaVersion")

    implementation(platform("org.jetbrains.kotlin:kotlin-bom"))
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("io.micrometer:micrometer-registry-prometheus")
    implementation("org.springframework.kafka:spring-kafka")
    implementation("org.postgresql:postgresql")
    implementation("org.flywaydb:flyway-database-postgresql")
    implementation("org.hibernate.validator:hibernate-validator")
    implementation("org.apache.httpcomponents.client5:httpclient5")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("no.nav.security:token-validation-spring:$tokenSupportVersion")
    implementation("no.nav.security:token-client-spring:$tokenSupportVersion")
    implementation("net.logstash.logback:logstash-logback-encoder:$logstashLogbackEncoderVersion")
    implementation("no.nav.helse.flex:sykepengesoknad-kafka:$sykepengesoknadKafkaVersion")
    implementation("org.apache.avro:avro:$avroVersion")
    implementation("io.confluent:kafka-connect-avro-converter:$confluentVersion")
    implementation("io.confluent:kafka-schema-registry-client:$confluentVersion")
    implementation("no.nav.paw.arbeidssokerregisteret.api:main-avro-schema:$arbeidssokerregisteretSchemaVersion")
    implementation("no.nav.paw.arbeidssokerregisteret.api:bekreftelsesmelding-schema:$bekreftelsesmeldingSchemaVersion")
    implementation("no.nav.paw.arbeidssokerregisteret.api:bekreftelse-paavegneav-schema:$bekreftelsePaaVegneAvSchemaVersion")

    testImplementation(platform("org.testcontainers:testcontainers-bom:$testContainersVersion"))
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.testcontainers:postgresql")
    testImplementation("org.testcontainers:kafka")
    testImplementation("org.awaitility:awaitility")
    testImplementation("org.amshove.kluent:kluent:$kluentVersion")
    testImplementation("no.nav.security:token-validation-spring-test:$tokenSupportVersion")
}

ktlint {
    version.set("1.5.0")
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
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

tasks {
    generateAvroProtocol {
        schema.forEach {
            source(zipTree(it))
        }
    }
}

tasks {
    runKtlintCheckOverTestSourceSet {
        dependsOn(generateTestAvroJava)
    }
}
