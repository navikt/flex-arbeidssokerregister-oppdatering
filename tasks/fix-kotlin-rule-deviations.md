# Fix Kotlin rule deviations in `src/main`

## Problem

Code in `src/main` was written before `.github/instructions/kotlin.instructions.md`.
Some of the code does not follow the rules in that file.
The code uses `!!`, dependencies that are not `private`, `findById().orElse(null)`, a getter method and
`?.let { } ?: run { }`.
Some scope function blocks are long, one nullable type has a non-null default, and one function has no expression
body.

## Goal

- `src/main` contains no `!!`.
- Each constructor dependency and logger in `src/main` is `private`.
- `FlexInternalController` uses `findByIdOrNull()`. It does not use `findById().orElse(null)`.
- `EnvironmentToggles` has no getter method for `naisClusterName`.
- `src/main` contains no `?.let { } ?: run { }`.
- `ArbeidssokerperiodeListener` and `SykepengesoknadListener` have no `try`-`catch` block inside a scope function.
- `ArbeidssokerperiodeResponse.periodebekreftelser` is not nullable.
- `ArbeidssokerperiodeBekreftelseProducer.lagBekreftelse` has an expression body.
- The behavior of the application does not change. All tests pass.

## Scope

- In scope: the classes in `src/main` that the Steps name.
- Out of scope: code in `src/test`, except the 3 assertions that depend on the type change in step 12.
  The task file `fix-test-rule-deviations.md` covers other test changes.
- Out of scope: JSON field names in `ArbeidssokerperiodeResponse`. They are an API contract.
- Out of scope: other refactoring in the same classes. The Kotlin rules forbid changes to unrelated code.

## Approach

Fix one rule at a time, in small changes that do not change the behavior.
When a missing value stops the flow, throw a domain exception with the relevant IDs in the message.
Use `ArbeidssokerperiodeException` or `PeriodebekreftelseException` when they fit.
The existing tests must verify that the behavior does not change.

## Steps

1. Remove each `!!` in `SykepengesoknadService`, for example on `soknad.friskTilArbeidVedtakId`,
   `arbeidssokerperiode.id` and the result of `hentKafkaKey`. Use a safe call, or `?:` with `return`, `throw` or a
   default value.
2. Remove each `!!` in `FlexInternalController`: on `arbeidssokerperiode.id` and on `this.id` in
   `tilArbeidssokerperiodeResponse` and `tilPeriodebekreftelseResponse`.
3. Remove each `!!` in `ArbeidssokerperiodePaaVegneAvProducer.tilPaaVegneAvStartMelding`.
4. Remove the `!!` after `.body(String::class.java)` in `ArbeidssokerregisterClient`.
5. Remove the `!!` from the 2 lookups in `clientConfigurationProperties.registration` in `RestClientConfig`. Throw an
   exception with the name of the registration.
6. Remove the `!!` on `it.id` in `TestdataResetListener`.
7. Make these dependencies `private`: `ArbeidssokerregisterClient.restClient`, `KafkaKeyGeneratorClient.restClient`,
   `ArbeidssokerperiodePaaVegneAvProducer.kafkaProducer`, `ArbeidssokerperiodeBekreftelseProducer.kafkaProducer` and
   `KafkaErrorHandler.log`. If another class uses one of them, change that class first.
8. Replace `findById().orElse(null)` with `findByIdOrNull()` in the 3 `update...` endpoints in
   `FlexInternalController`.
9. Remove the function `EnvironmentToggles.naisClusterName()`. No class calls it now.
10. Replace `?.let { } ?: run { }` with `if`-`else` in `KafkaErrorHandler.handleRemaining`.
11. Move the `try`-`catch` block out of `cr.value().also { }` in `ArbeidssokerperiodeListener` and
    `SykepengesoknadListener`. Use a local `val`.
12. Change `ArbeidssokerperiodeResponse.periodebekreftelser` from `List<PeriodebekreftelseResponse>?` to
    `List<PeriodebekreftelseResponse>`. Remove the 3 now-unnecessary `!!` assertions on this field in
    `FlexInternalControllerIntegrationTest`. Expected result: tests compile with `CI=true`.
13. Change `ArbeidssokerperiodeBekreftelseProducer.lagBekreftelse` to an expression body. Remove the local `val`.
14. Run `./gradlew test`. Expected result: all tests pass.
15. Run a search for `!!`, `orElse(null)` and `?: run {` in `src/main`. Expected result: no matches.

## Notes

- Each step is a separate refactoring. Do the steps in one or more pull requests.
- An exception from a Kafka listener makes `KafkaErrorHandler` retry the message.
  Make sure that a new exception does not change which messages the application retries.
- In step 12, the JSON output does not change, because the field is never null now.
- Step 12 also changes 3 test assertions. The non-null type makes their `!!` assertions compiler warnings.
  With `CI=true`, these warnings are errors.

## Progress

Replace ⬜ with ✅ when a step is done.

| Step | Task                                              | Status |
|------|---------------------------------------------------|--------|
| 1    | Remove `!!` in `SykepengesoknadService`           | ✅      |
| 2    | Remove `!!` in `FlexInternalController`           | ✅      |
| 3    | Remove `!!` in `PaaVegneAvProducer`               | ✅      |
| 4    | Remove `!!` in `ArbeidssokerregisterClient`       | ✅      |
| 5    | Remove `!!` in `RestClientConfig`                 | ✅      |
| 6    | Remove `!!` in `TestdataResetListener`            | ✅      |
| 7    | Make dependencies `private`                       | ✅      |
| 8    | Use `findByIdOrNull()`                            | ✅      |
| 9    | Remove the getter in `EnvironmentToggles`         | ✅      |
| 10   | Use `if`-`else` in `KafkaErrorHandler`            | ✅      |
| 11   | Move `try`-`catch` out of `also`                  | ✅      |
| 12   | Make `periodebekreftelser` non-null               | ✅      |
| 13   | Use an expression body in `lagBekreftelse`        | ✅      |
| 14   | Verify: all tests pass                            | ⬜      |
| 15   | Verify: no deviations remain                      | ⬜      |
