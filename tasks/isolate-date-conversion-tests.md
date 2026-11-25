# Isolate date-conversion tests

## Problem

`konverterOsloDatoTilInstantSommertid` and `konverterOsloDatoTilInstantVintertid` are in `PeriodebekreftelseIntegrationTest`.
Both test only `toInstantAtStartOfDayOsloTid()`, but inherit the full Spring context and infrastructure from `FellesTestOppsett`.
These checks do not need PostgreSQL, Kafka, OAuth or HTTP servers.
Both [Fix test rule deviations](fix-test-rule-deviations.md) and
[Simplify producer and client tests](simplify-producer-and-client-tests.md) leave these tests outside their scope.

## Goal

- Both date-conversion checks run as unit tests without a Spring context, database, Kafka or network access.
- The existing summer-time and winter-time inputs and expected results remain unchanged.
- `PeriodebekreftelseIntegrationTest` retains all its integration flow checks.
- Both affected classes pass alone and together.
- Kotlin lint checks pass.

## Scope

- In scope: move the two date-conversion tests from `PeriodebekreftelseIntegrationTest` to
  `src/test/kotlin/no/nav/helse/flex/sykepengesoknad/ToInstantAtStartOfDayOsloTidTest.kt`.
- In scope: use Norwegian sentences in backticks for the moved test method names.
- Out of scope: production code, dependencies, shared test setup and other test cases.
- Out of scope: producer and client tests, shared HTTP request queues and additional date-conversion cases.

## Approach

Create a plain JUnit 5 test class in the same package as the date-conversion function.
Call the real extension function directly and keep the existing Kotest assertions.
Do not extend `FellesTestOppsett` or add Spring annotations, fakes or server setup.
Remove only the two moved methods from `PeriodebekreftelseIntegrationTest`.
Keep imports that its remaining integration tests use.

## Steps

1. Read `.github/instructions/kotlin.instructions.md` and `.github/instructions/tests.instructions.md`.
2. Create `ToInstantAtStartOfDayOsloTidTest` and move both date-conversion checks into it.
   Preserve these input and expected-result pairs:

   | Input | Expected result |
   |-------|-----------------|
   | `2025-05-10` | `2025-05-09T22:00:00Z` |
   | `2025-01-10` | `2025-01-09T23:00:00Z` |

3. Remove the two original test methods from `PeriodebekreftelseIntegrationTest`.
   Do not change its flow checks or shared setup.
4. Run the new unit test class alone with the Gradle selector in Notes.
   Expected result: both checks pass without Docker or a Spring context.
5. Run `PeriodebekreftelseIntegrationTest` alone with approved Docker access.
   Expected result: its remaining integration tests pass.
6. Run both affected classes together and run `./gradlew ktlintCheck`.
   Expected result: both classes and Kotlin lint checks pass.

## Notes

- Run the unit tests with
  `./gradlew test --tests 'no.nav.helse.flex.sykepengesoknad.ToInstantAtStartOfDayOsloTidTest'`.
- Run the integration tests with
  `./gradlew test --tests 'no.nav.helse.flex.sykepengesoknad.PeriodebekreftelseIntegrationTest'`.
- Run both classes together with
  `./gradlew test --tests 'no.nav.helse.flex.sykepengesoknad.ToInstantAtStartOfDayOsloTidTest' --tests 'no.nav.helse.flex.sykepengesoknad.PeriodebekreftelseIntegrationTest'`.
- Follow the Docker access checks and blocked-test reporting rules in `.github/instructions/tests.instructions.md`
  before running the integration class, alone or together with the unit class.
- This task does not depend on the producer and client conversion task.
- No deploy is needed because this task changes tests only.

## Progress

Replace ⬜ with ✅ when a step is done.

| Step | Task | Status |
|------|------|--------|
| 1 | Read Kotlin and test instructions | ⬜ |
| 2 | Move date-conversion checks to unit tests | ⬜ |
| 3 | Remove original date-conversion methods | ⬜ |
| 4 | Verify unit tests alone | ⬜ |
| 5 | Verify integration tests alone | ⬜ |
| 6 | Verify both classes together and lint | ⬜ |
