# Simplify producer and client tests

## Problem

Three producer test classes start the full Spring context for checks already covered by integration flows.
Two HTTP client test classes also start PostgreSQL and Kafka, but only call one client through `MockWebServer`.
The client tests have request, response and error checks that the integration flows do not cover.
Some client tests leave recorded HTTP requests in the shared server queues.
The decisions in step 4 of `fix-test-rule-deviations.md` select deletion for the producer tests and unit tests for the clients.

## Goal

- The three producer test classes do not exist, and their existing integration coverage remains.
- `KafkaKeyGeneratorClientTest` and `ArbeidssokerregisterClientTest` run without a Spring context, database, Kafka, OAuth server or network access.
- The client unit tests retain their current request, response and HTTP error checks, except the OAuth header checks.
- `VedtaksperiodeIntegrationTest` checks both clients' OAuth headers through the real configured clients.
- Each affected test class passes alone, and the affected tests pass together.
- Kotlin lint checks pass.

## Scope

- In scope: delete `ArbeidssokerperiodeBekreftelseProducerTest`, `ArbeidssokerperiodePaaVegneAvProducerTest` and `ArbeidssokerperiodeStartStoppProducerTest`.
- In scope: convert `KafkaKeyGeneratorClientTest` and `ArbeidssokerregisterClientTest` to unit tests.
- In scope: a shared `FakeClientHttpRequestFactory` in `src/test/kotlin/no/nav/helse/flex/arbeidssokerregister/`.
- In scope: HTTP request assertions in the successful application flow in `VedtaksperiodeIntegrationTest`.
- Out of scope: production code, dependencies, API contracts, Kafka messages, database schema and deployment configuration.
  The existing client constructors support the selected approach.
- Out of scope: changes to `FellesTestOppsett` and `TestKafkaConfig`.
  The unit tests must not use either setup.
- Out of scope: removing `@Order`, changing date-conversion tests or adding checks for unrelated coverage gaps.
  The source task handles test independence; this task preserves the selected tests' current checks.

## Approach

Delete the producer tests because existing integration flows already cover their assertions and serialization.
Construct each HTTP client with a real `RestClient` and a hand-written in-memory HTTP transport fake.
Keep real JSON conversion and HTTP status handling, but remove all server and Spring context setup from the unit tests.
Move the OAuth header assertions to the existing application flow, which uses the production client configuration.
Do not add tests that call a client directly while starting the full Spring context.

## Steps

1. Delete `ArbeidssokerperiodeBekreftelseProducerTest`, `ArbeidssokerperiodePaaVegneAvProducerTest` and `ArbeidssokerperiodeStartStoppProducerTest`.
2. Convert `KafkaKeyGeneratorClientTest` to unit tests with `FakeClientHttpRequestFactory`.
   Retain the successful key response, POST path and HTTP 404/500 exception checks.
3. Convert `ArbeidssokerregisterClientTest` to unit tests with the same `FakeClientHttpRequestFactory`.
   Retain both literal-JSON response cases, metadata checks, POST path and `siste=true` query parameter.
   Do step 2 first, because it introduces the shared fake.
4. Extend the successful application flow in `VedtaksperiodeIntegrationTest` with both clients' OAuth header assertions.
   Consume and check the requests from that flow instead of checking only that a request exists.
   Do steps 2 and 3 first, so the old client tests no longer leave requests in the shared server queues.
5. Run each affected class alone with the Gradle selectors in Notes.
   Expected result: both client unit tests pass without Docker, and each integration class passes with approved Docker access.
6. Run the affected classes together and run `./gradlew ktlintCheck`.
   Expected result: all selected tests and Kotlin lint checks pass.

## Notes

- Read `.github/instructions/kotlin.instructions.md` and `.github/instructions/tests.instructions.md` before implementation.
- Before step 4, complete step 6 of `fix-test-rule-deviations.md`.
  It makes `VedtaksperiodeIntegrationTest` independent; this task must not mix that change with OAuth assertions.
- `RestClient` is a Spring library object, not a Spring application context.
  Build it directly in the unit tests and inject the fake request factory.
- The fake must capture the request method, URI, headers and body in memory.
  It must return in-memory response bodies, headers and HTTP statuses.
  Do not use `MockWebServer`, real sockets or a mocking framework in the unit tests.
- Use JSON responses with the Content-Type expected by the real `RestClient` converters.
  Keep the active-period and closed-period literal JSON from `ArbeidssokerregisterClientTest`.
- Do not add a fake Bearer token assertion to the unit tests.
  The real configured clients in `VedtaksperiodeIntegrationTest` must provide the OAuth header checks.
- Keep each fake and response setup local to its test, so tests do not share request or response queues.
- Existing producer coverage remains in `PeriodebekreftelseIntegrationTest`, `VedtaksperiodeIntegrationTest`,
  `ArbeidssokerperiodeServiceIntegrationTest` and `UtgattSoknadIntegrationTest`.
- Run these six classes alone with `./gradlew test --tests '<fully qualified class name>'`:
  `no.nav.helse.flex.arbeidssokerregister.KafkaKeyGeneratorClientTest`,
  `no.nav.helse.flex.arbeidssokerregister.ArbeidssokerregisterClientTest`,
  `no.nav.helse.flex.sykepengesoknad.PeriodebekreftelseIntegrationTest`,
  `no.nav.helse.flex.sykepengesoknad.VedtaksperiodeIntegrationTest`,
  `no.nav.helse.flex.arbeidssokerperiode.ArbeidssokerperiodeServiceIntegrationTest` and
  `no.nav.helse.flex.sykepengesoknad.UtgattSoknadIntegrationTest`.
- For step 6, pass all six class selectors to one `./gradlew test` command.
  Follow the Docker access checks and blocked-test rules in `.github/instructions/tests.instructions.md`.
- No deploy is needed because this task changes tests only.

## Progress

Replace ⬜ with ✅ when a step is done.

| Step | Task | Status |
|------|------|--------|
| 1 | Delete duplicate producer tests | ⬜ |
| 2 | Convert Kafka key client tests | ⬜ |
| 3 | Convert register client tests | ⬜ |
| 4 | Check OAuth headers in application flow | ⬜ |
| 5 | Verify affected classes alone | ⬜ |
| 6 | Verify affected classes together and lint | ⬜ |
