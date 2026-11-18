---
applyTo: "src/test/**"
---

# Tests

## Test setup

- Tests use JUnit 5 and Kotest assertions. All tests are in `src/test/kotlin`, in the same package as the code they test.
- Integration tests extend `FellesTestOppsett`. It starts PostgreSQL and Kafka with Testcontainers, and the full Spring context.
- `FellesTestOppsett` replaces outbound APIs with `MockWebServer` and Azure AD with `MockOAuth2Server`.
- `TestKafkaConfig` creates the topics, test consumers, test producers and a `MockSchemaRegistryClient` for Avro.
- Run all tests with `./gradlew test`. Integration tests need a running Docker daemon.
- Run one test class with `./gradlew test --tests '<fully qualified class name>'`.

## Running tests

Apply these steps inside and outside sandboxes, including after production-code changes.
Documentation-only changes do not need application builds or tests.

1. Select the smallest set of tests that covers the change.
2. Read their setup to identify Docker requirements. Tests that extend `FellesTestOppsett` start containers during class initialization.
3. Choose an available test runner using the guidance below.
4. Run the affected tests if that runner has the required access.
5. If no permitted runner can run Docker-based tests, run applicable unit tests and Docker-free checks, such as `./gradlew ktlintCheck`.
6. Report tests that were not run and the reason.
7. For blocked tests, give the exact Gradle test command and ask the user to run it with approved Docker access.

### IntelliJ MCP runner

When available and permitted by the active sandbox policy, use IntelliJ MCP test tools to run the selected tests.
Follow the Gradle preparation steps in `AGENTS.md` before IntelliJ builds or tests.
IntelliJ can have Docker access even when the agent's shell does not.
A failed shell Docker check does not establish whether the IntelliJ runner has access.
Do not assume that an available MCP server guarantees Docker access.

Use the test execution tools, not an IDE terminal, to run the selected tests.
Read the execution result before reporting success. Starting a test run is not proof that it passed.
If the tools are unavailable or return an error, use the shell runner.
For Docker-based tests, its Docker access checks must pass first.
Do not use IntelliJ to bypass an explicit sandbox restriction on tool use.

### Shell runner

1. Before Docker-based tests, check that the Docker CLI exists with `command -v docker`.
2. If it exists, check daemon access with `docker info --format '{{.ServerVersion}}'` in the current environment.
   Stop the check if the daemon does not respond.
3. If the check succeeds, run the affected tests with the commands above.
4. If Docker is missing, denied, or unresponsive, do not run Docker-based tests, unfiltered `./gradlew test`, or `./gradlew build` through the shell.

Run applicable unit tests with explicit class selectors.

Do not select tests by the `IntegrationTest` suffix alone. Some existing integration tests do not use that suffix.
For example, this unit test does not need Docker:

```shell
./gradlew test --tests 'no.nav.helse.flex.sykepengesoknad.BeregnGraceMSTest'
```

Docker CLI presence does not prove daemon access.
A successful daemon check does not prove that image access, container startup, or mapped ports will work.

### Results and restrictions

If infrastructure restrictions block tests, stop the attempt in that runner.
Use another permitted runner with the required access, or report blocked verification.
Do not classify assertion failures as infrastructure restrictions.
Do not report skipped or blocked tests as passing.

Do not rely on an unverified sandbox detection variable.
Do not start Docker, change sandbox permissions, or bypass restrictions.
Do not change Docker contexts, socket paths, or environment variables to bypass a restriction.
Do not silently disable tests or weaken the shared test setup.

## Test types

Every test must be a unit test or an integration test.

- Unit test: test business logic in isolation. Do not use a Spring context, the database, Kafka or the network.
- In a unit test, replace collaborators with simple hand-written fakes.
- Integration test: test a flow through real infrastructure, for example Kafka in, database write and Kafka out.
- In an integration test, extend `FellesTestOppsett`. Do not create other Spring contexts or Testcontainers.
- Do not write tests between these types, for example a Spring context with most beans mocked. These tests are slow and test little.

## Choosing a type

- Write unit tests for business logic and transformations, for example `beregnGraceMS` in `BeregnGraceMSTest`.
- Write integration tests for flows across Kafka, the database or outbound APIs.

## Conventions

- Name a unit test class `<Class>Test`. Name an integration test class `<Flow or class>IntegrationTest`.
- Write test method names as Norwegian sentences in backticks that describe the expected behavior.
- Assert with Kotest matchers, for example `shouldBe` and `shouldHaveSize`. Use `assertThrows` from JUnit for exceptions.
- Compare timestamps with `should be within seconds of` from `FellesTestOppsett`.
- Use `FNR`, `VEDTAKSPERIODE_ID` and `lagSoknad()` from `FellesTestOppsett` for test data.
- Put other test data in private `lag...` and `lagre...` functions, and shared checks in private `verifiser...` functions.
- Start a flow with the service method that the listener calls, for example `sykepengesoknadService.behandleSoknad()`.
- Read output messages with `waitForRecords()` on the consumers in `FellesTestOppsett`. Use `waitForRecords(0)` to check that no message is sent.
- Give responses from outbound APIs with `enqueue()` on `kafkaKeyGeneratorMockWebServer` or `arbeidssokerperiodeMockWebServer`.
- Delete database rows with `slettFraDatabase()` in `@AfterEach` or `@BeforeEach`. Reason: all integration tests share one database.
- Make each test independent. Do not use `@Order` in new tests.
- Get Spring beans from the fields in `FellesTestOppsett`. Add a missing bean there as an `@Autowired lateinit var` field.
  Reason: JUnit creates the test class, so the Kotlin rule for constructor injection does not apply here.

## Rules

- Follow the existing setup. Do not add test frameworks or libraries.
- Run the affected tests after you write or change them.
