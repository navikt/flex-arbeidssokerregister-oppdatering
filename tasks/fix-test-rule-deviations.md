# Fix test rule deviations in `src/test`

## Problem

Tests in `src/test` were written before `.github/instructions/tests.instructions.md`.
Some tests start the full Spring context through `FellesTestOppsett`, but test one class and not a flow.
These tests are neither unit tests nor integration tests, and none of them mock beans.
`TestdataResetListenerTest`, `VedtaksperiodeIntegrationTest` and `PeriodebekreftelseIntegrationTest` use `@Order`.
In `TestdataResetListenerTest`, later tests use data that earlier tests save.

## Goal

- `ApplicationContextTest` does not exist.
- A decision exists for each test that is neither a unit test nor an integration test.
- No test in `src/test` uses `@Order`.
- Each test passes when it runs alone.
- All tests pass.

## Scope

- In scope: `ApplicationContextTest`.
- In scope: the investigation of `ArbeidssokerperiodeBekreftelseProducerTest`,
  `ArbeidssokerperiodePaaVegneAvProducerTest`, `ArbeidssokerperiodeStartStoppProducerTest`,
  `KafkaKeyGeneratorClientTest` and `ArbeidssokerregisterClientTest`.
- In scope: `@Order` in `TestdataResetListenerTest`, `VedtaksperiodeIntegrationTest` and
  `PeriodebekreftelseIntegrationTest`.
- Out of scope: changes to the 5 tests in the investigation. A new task must do the changes after the decision.
- Out of scope: code in `src/main`. The task file `fix-kotlin-rule-deviations.md` covers it.
- Out of scope: changes to `FellesTestOppsett` and `TestKafkaConfig`, unless a test needs a missing bean or helper.

## Approach

Delete `ApplicationContextTest`, because all integration tests already start the Spring context.
Investigate the other tests between the two test types before a decision.
For each test, find out if an integration test covers the same behavior, and if a unit test with fakes can replace it.
Make each test with `@Order` independent, so that it saves its own data and does not need the order.

## Steps

1. Delete `ApplicationContextTest`.
2. Investigate the producer tests `ArbeidssokerperiodeBekreftelseProducerTest`,
   `ArbeidssokerperiodePaaVegneAvProducerTest` and `ArbeidssokerperiodeStartStoppProducerTest`.
   Each test sends one message through a producer and reads it back.
3. Investigate the client tests `KafkaKeyGeneratorClientTest` and `ArbeidssokerregisterClientTest`.
   Each test sends requests from one HTTP client to `MockWebServer`.
4. Write the decision for each test in steps 2 and 3: delete it, change it to a unit test, or rename it to
   `<Flow or class>IntegrationTest`. Create a task file for the changes.
5. Remove `@Order` from `TestdataResetListenerTest`. Make each test save the data that it needs.
6. Remove `@Order` from `VedtaksperiodeIntegrationTest`. Make each test independent.
7. Remove `@Order` from `PeriodebekreftelseIntegrationTest`. Make each test independent.
8. Run each changed test class alone with `./gradlew test --tests '<fully qualified class name>'`.
   Expected result: all tests pass.
9. Run `./gradlew test`. Expected result: all tests pass, and no test uses `@Order`.

## Notes

- Integration tests need a running Docker daemon.
- `.github/instructions/tests.instructions.md` forbids `@Order` in new tests only. This task removes it from the
  existing tests too.
- All integration tests share one database. Delete rows with `slettFraDatabase()` in `@BeforeEach` or `@AfterEach`.
- Remove the `@TestMethodOrder` annotation and the unused imports when a class no longer uses `@Order`.

## Progress

Replace ⬜ with ✅ when a step is done.

| Step | Task                                                     | Status |
|------|----------------------------------------------------------|--------|
| 1    | Delete `ApplicationContextTest`                          | ✅      |
| 2    | Investigate the producer tests                           | ⬜      |
| 3    | Investigate the client tests                             | ⬜      |
| 4    | Write the decisions and a new task                       | ⬜      |
| 5    | Remove `@Order` from `TestdataResetListenerTest`         | ⬜      |
| 6    | Remove `@Order` from `VedtaksperiodeIntegrationTest`     | ⬜      |
| 7    | Remove `@Order` from `PeriodebekreftelseIntegrationTest` | ⬜      |
| 8    | Verify: each changed class passes alone                  | ⬜      |
| 9    | Verify: all tests pass                                   | ⬜      |
