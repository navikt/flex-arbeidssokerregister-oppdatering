# AGENTS.md

## Purpose

This service uses sickness benefit applications (Sykepengesøknader) for Friskmeldt til Arbeidsformidling to maintain job
seeker confirmations in Arbeidssøkerregisteret.  
It reports the start and stop of job seeker registrations to `sykepengesoknad-backend`.

Glossary:

- Friskmeldt til Arbeidsformidling: a sickness benefit scheme for people who are fit for work and seek a new job.
- Arbeidssøkerregisteret: NAV's job seeker register.

## Tech stack

- Kotlin, Spring Boot and Gradle with Kotlin DSL.
- Spring Kafka, Apache Avro, Confluent Schema Registry and Jackson for messages.
- Spring Data JDBC, PostgreSQL and Flyway for persistence.
- JUnit, Kotest assertions, Testcontainers, MockWebServer and MockOAuth2Server for tests.

## Project structure

Under `src/main/kotlin/no/nav/helse/flex`, a domain package holds its services and repositories together. An integration
package holds listeners, producers and clients.

- `arbeidssokerperiode/`: registration period state, persistence and lifecycle.
- `sykepengesoknad/`: søknad processing, confirmation persistence and messages to `sykepengesoknad-backend`.
- `arbeidssokerregister/`: Kafka and HTTP integration with Arbeidssøkerregisteret.
- `api/`: internal HTTP endpoints and caller validation.
- `kafka/`, `config/`: shared Kafka and HTTP server configuration.
- `testdata/`: test data reset for the dev environment.
- `src/main/resources/`: application configuration and Flyway migrations in `db/migration/`.
- `buildSrc/`: the `avro-codegen` plugin. It generates Java classes from external Avro schemas into `build/`.
- `nais/`: deployment configuration and Kafka topic definitions.
- `documentation/`: knowledge about the project, its architecture and language related instructions. 
- `tasks/`: task plans and issues related to the project.

## Commands

Run the commands from the repository root with a JDK that matches the JVM target in `build.gradle.kts`.

| Action               | Command                                                |
|----------------------|--------------------------------------------------------|
| Build, test and lint | `./gradlew build`                                      |
| Run all tests        | `./gradlew test`                                       |
| Run a single test    | `./gradlew test --tests '<fully qualified test name>'` |
| Lint                 | `./gradlew ktlintCheck`                                |
| Format               | `./gradlew ktlintFormat`                               |

Before running tests, read `.github/instructions/tests.instructions.md`, even when only production code changes.
This rule includes `./gradlew build`, which also runs tests.
Integration tests need Docker access. Unit tests do not.
Follow the Docker access checks and blocked-test reporting rules in that file.

With `CI=true`, the compiler treats warnings as errors.
Do not start the application unless the user explicitly requests it.

## IntelliJ MCP

When available, prefer IntelliJ MCP server tools for code intelligence:

- Find symbol usages and references.
- Navigate to declarations and implementations.
- Inspect class hierarchies and type information.
- Resolve imports and module structure.

Use `rg` or `grep` for non-source files, such as configuration, logs and documentation.
Use them for content patterns when the IntelliJ tools are not suitable, return an error, or are unavailable.

## Avro files

On a clean checkout, or when generated Avro classes are missing, run `./gradlew classes` before IntelliJ builds or tests.
Gradle generates the Avro classes and compiles the main sources without running tests.
Refresh the Gradle project in IntelliJ after generation.
Do not rely on an IntelliJ build alone to generate missing Avro classes.

## Rules

The developer handles branching, commits and worktrees.
Do not create or switch branches or worktrees, or create commits, unless the user explicitly requests it.

Ask the user before you:

- Add or upgrade dependencies, plugins or Avro schema versions.
- Change Kafka topic names, message formats or message keys.
- Change the database schema or Flyway migrations.
- Change HTTP API contracts.
- Change the deployment configuration in `nais/`.

Do not edit the generated Avro classes. Reason: the build generates them again from the schema dependencies.
Do not commit secrets or environment-specific values.
