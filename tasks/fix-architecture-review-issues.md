# Fix issues from the architecture review

## Problem

The review of section 8 in `documentation/application-architecture.md` found issues that need a code change.
This file collects them. Each issue has an id, for example `I1`.

- I1: `avroKafkaListenerContainerFactory` in `KafkaConfig` uses `KafkaAvroDeserializer` directly. It has no
  `ErrorHandlingDeserializer`. A record on `paw.arbeidssokerperioder-v1` that cannot be deserialized makes `poll()`
  throw. `DefaultErrorHandler.handleOtherException` cannot handle the error, because it has no record information.
  The container polls the same offset again in a loop with no back-off, in all clusters.
- I2: `ArbeidssokerperiodeBekreftelseProducer.lagBekreftelse` uses `UUID.randomUUID()` as the Bekreftelse id. The send
  occurs before the database commit. If the commit fails, the retry sends a new Bekreftelse with a new id. PAW cannot
  detect the duplicate.
- I3: `ArbeidssokerperiodeStartStoppProducer.send` does not wait for the broker ack and has no callback. A failed send
  in Flows D and E is lost with no error log, and the transaction commits.
- I4: `FlexInternalController.updateArbeidssokerperiodeId` saves the new `arbeidssokerperiode_id`, and then sends a
  PaaVegneAv `Start`. No transaction contains both. A failed send leaves the new id in the database. An id that is not
  a UUID is saved, and then the producer throws.
- I4: The endpoint does not set `sendt_paavegneav`. It also does not clear `avsluttet_mottatt` and
  `avsluttet_tidspunkt`, as Flow D does. If the old registry period ended, Flow E then ignores the end of the new
  registry period.
- I5: `FlexInternalController.updateVedtaksperiodeTom` saves the new `vedtaksperiode_tom` and sends no Kafka message.
  The grace period in Arbeidssøkerregisteret comes from the PaaVegneAv `Start`, so it still uses the old `tom`. If
  `tom` moves later, PAW can end the registry period before the new `tom`.
- I6: `Arbeidssokerperiode` has no `@Version`, and `save` writes all columns. `SykepengesoknadListener`,
  `ArbeidssokerperiodeListener` and Flow F can update the same row at the same time. One of the updates is then lost.
- I6: Flow A checks the registry period over HTTP, and then commits later. A Flow E record for that registry period
  can run between the check and the commit. Flow E then finds no row, and the end of the registry period is lost.
- I7: `SykepengesoknadService.behandleUtgattSoknad` filters on `avsluttetAarsak != UTGAATT`. The filter has no effect.
  Only `lagreAvsluttetAarsak` writes `avsluttet_aarsak`, and it always sets `sendt_avsluttet` too.
- I8: The migration `v21__slett_bigquery_datastream_grants.sql` starts with a lowercase `v`. Flyway does not recognize
  the name and skips the file with no error. The app user can still have `REPLICATION`.
- I9: `Application` has `@EnableScheduling`, but no `@Scheduled` method exists in `src/main`.

## Goal

- I1: A record on `paw.arbeidssokerperioder-v1` that cannot be deserialized goes to `KafkaErrorHandler`.
- I1: `KafkaErrorHandler` retries the record with the same back-off as other errors. It does not skip the record.
- I1: The log has one error for each attempt, with the topic, partition and offset.
- I2: Each send of a Bekreftelse for the same søknad has the same Bekreftelse id.
- I2: PAW confirms that it ignores a Bekreftelse with an id that it already has.
- I3: `ArbeidssokerperiodeStartStoppProducer.send` waits for the broker ack. A failed send throws, and the database
  transaction rolls back.
- I4: The save and the PaaVegneAv `Start` in `oppdater-arbeidssokerperiode-id` run in one database transaction. A
  failed send, also for an id that is not a UUID, leaves the row unchanged.
- I4: `oppdater-arbeidssokerperiode-id` sets `sendt_paavegneav` and clears `avsluttet_mottatt` and
  `avsluttet_tidspunkt`, as Flow D does.
- I5: PAW confirms that a second PaaVegneAv `Start` replaces the grace period.
- I5: `oppdater-tom` sends a PaaVegneAv `Start` with a grace period from the new `tom` when the PaaVegneAv is active.
  The PaaVegneAv is active when `sendt_avsluttet` and `avsluttet_mottatt` are null.
- I5: The save and the send in `oppdater-tom` run in one database transaction.
- I6: Each transaction that reads and then writes `arbeidssokerperiode` rows for a person first takes a PostgreSQL
  advisory lock for the `fnr` of the person.
- I6: Flow A takes the lock before it calls Arbeidssøkerregisteret. Flow E then waits for the Flow A commit and
  finds the row.
- I6: The lock is transaction-scoped. PostgreSQL releases it at commit or rollback.
- I7: `behandleUtgattSoknad` filters only on `sendtAvsluttet == null`.
- I8: A new migration `V24` contains the SQL from `v21__slett_bigquery_datastream_grants.sql`.
- I8: The file `v21__slett_bigquery_datastream_grants.sql` is deleted.
- I8: The application starts, and Flyway validation passes.
- I9: `Application` has no `@EnableScheduling`.
- The behavior for records that can be deserialized does not change. All tests pass.

## Scope

- In scope: `KafkaConfig.avroKafkaListenerContainerFactory` and `KafkaErrorHandler`.
- Out of scope: JSON parse errors in `SykepengesoknadListener`. They already retry with back-off, which is a design
  decision (section 2.0 in `documentation/application-architecture.md`).
- In scope: `ArbeidssokerperiodeBekreftelseProducer` and `BekreftelseMelding`.
- In scope: `ArbeidssokerperiodeStartStoppProducer`.
- In scope: `FlexInternalController.updateArbeidssokerperiodeId` and a new `@Transactional` service method.
- In scope: `FlexInternalController.updateVedtaksperiodeTom`.
- Out of scope: `oppdater-fom`. `fom` does not affect the grace period.
- In scope: `ArbeidssokerperiodeRepository`, `SykepengesoknadService.behandleSoknad`,
  `ArbeidssokerperiodeService.behandlePeriode` and the service methods from I4 and I5.
- In scope: `SykepengesoknadService.behandleUtgattSoknad`.
- In scope: `src/main/resources/db/migration`.
- Out of scope: a rename of `v21` to `V21`. V22 and V23 are already applied, so Flyway validation then fails and the
  application does not start.
- In scope: `Application`.
- Out of scope: the selection of rows in Flow C. It stops all open rows for the person, and it ignores
  `ignorerArbeidssokerregister`. These are design decisions.
- Out of scope: `TestdataResetListener`. It runs only in dev and has no transaction.
- Out of scope: `@Version` and targeted `UPDATE` queries. The advisory lock prevents lost updates with no schema
  change.
- Out of scope: validation of the id with HTTP 400. The rollback is sufficient for an internal tool.
- Out of scope: a transactional outbox. The name-based id solves the duplicate problem with a smaller change.
- Out of scope: a dead-letter topic. Unbounded retry is a design decision (section 2.0).

## Approach

I1: Wrap `KafkaAvroDeserializer` in `ErrorHandlingDeserializer`. Then the container gives the failed record to the
error handler. `DefaultErrorHandler` classifies `DeserializationException` as not retryable by default. Make it
retryable in `KafkaErrorHandler`, so that a failed record blocks the partition as other errors do. A schema registry
outage also gives a deserialization error, and a retry can then succeed.

I2: Make the Bekreftelse id a name-based UUID (`UUID.nameUUIDFromBytes`) from the søknad id. The id is then the same
for each retry. This helps only if PAW deduplicates on the id, so confirm this first.

I3: Call `.get()` on the send, as the Avro producers do. Then a failed send uses the same retry rules as other errors.

I4: Move the save and the send to one `@Transactional` service method. The controller calls this method. The PaaVegneAv
producer waits for the ack, so a failed send rolls back the save. The method updates the same fields as
`ArbeidssokerperiodeService.restartArbeidssokerperiode`, because a new id is a manual restart.

I5: Use the same pattern as I4. A `@Transactional` service method saves the new `tom` and sends a PaaVegneAv `Start`
with grace from `beregnGraceMS`. Do not send a `Start` when the PaaVegneAv is not active, because that starts a
PaaVegneAv that was stopped.

I6: Add a repository method that calls `pg_advisory_xact_lock(hashtext(:fnr))`. Call it at the start of each
transaction, before the first read of `arbeidssokerperiode`. Use the `fnr` as the lock key in all flows, so that all
processing for one person runs in sequence. Flow E gets the `fnr` from `Periode.identitetsnummer`. Flow F has only the
row id. It must read the row, take the lock, and then read the row again. Flow A takes the lock before its HTTP calls,
and holds it during them. This closes the race with Flow E. It is acceptable, because the lock blocks only one person.
Do I6 after I4 and I5, because it uses their service methods.

I7: Remove the filter. The existing tests for Flow C must verify that the behavior does not change.

I8: Move the SQL to a new migration with the next version. The SQL checks that the role exists, so it is safe to run
in all environments.

I9: Remove the annotation. No code uses it.

## Steps

1. I1: Set `ErrorHandlingDeserializer` as the value deserializer in `avroKafkaListenerContainerFactory`, with
   `KafkaAvroDeserializer` as the delegate.
2. I1: Make `DeserializationException` retryable in `KafkaErrorHandler`.
3. I1: Add an integration test that sends a record that cannot be deserialized to `paw.arbeidssokerperioder-v1`.
   Expected result: the record is not acknowledged, and the offset does not move.
4. I2: Ask PAW if they ignore a Bekreftelse with an id that they already have. Expected result: PAW confirms it. If
   not, stop work on I2.
5. I2: Add the søknad id to `BekreftelseMelding`. Make `lagBekreftelse` use a name-based UUID from it as the id.
6. I2: Add a test. Expected result: two sends for the same søknad have the same Bekreftelse id.
7. I3: Call `.get()` on the send in `ArbeidssokerperiodeStartStoppProducer.send`.
8. I3: Add a test. Expected result: a failed send of a `StartStoppMelding` rolls back the database change.
9. I4: Move the save and the send in `updateArbeidssokerperiodeId` to one `@Transactional` service method. Make the
   method update the same fields as `restartArbeidssokerperiode`.
10. I4: Add tests. Expected result: a failed send leaves the row unchanged. A successful call sets
    `sendt_paavegneav` and clears `avsluttet_mottatt` and `avsluttet_tidspunkt`.
11. I5: Ask PAW if a second PaaVegneAv `Start` replaces the grace period. Expected result: PAW confirms it. If not,
    stop work on I5.
12. I5: Move the save in `updateVedtaksperiodeTom` to a `@Transactional` service method. Make the method send a
    PaaVegneAv `Start` with grace from the new `tom` when the PaaVegneAv is active.
13. I5: Add tests. Expected result: an active PaaVegneAv gets a new `Start`. A stopped PaaVegneAv gets no `Start`.
14. I6: Add a repository method in `ArbeidssokerperiodeRepository` that takes a transaction-scoped advisory lock for
    an `fnr`.
15. I6: Call the lock method first in `behandleSoknad`, `behandlePeriode` and the service methods from I4 and I5.
16. I6: Add tests. Expected result: two concurrent transactions for the same `fnr` run in sequence, and no update is
    lost. A Flow E record that comes during Flow A finds the new row.
17. I7: Remove the filter `avsluttetAarsak != UTGAATT` in `behandleUtgattSoknad`. Expected result: all tests pass.
18. I8: Add `V24__slett_bigquery_datastream_grants.sql` with the SQL from `v21__slett_bigquery_datastream_grants.sql`.
    Delete the `v21` file.
19. I8: Deploy the application to dev. Expected result: Flyway applies V24, and the app user has `NOREPLICATION`.
20. I9: Remove `@EnableScheduling` from `Application`. Expected result: all tests pass.
21. Search for `fix-architecture-review-issues.md` in `documentation/application-architecture.md`. Expected result: no
    matches for the issues that are done.
22. Deploy the application to dev. Expected result: the consumers of `flex.sykepengesoknad` and
    `paw.arbeidssokerperioder-v1` process records.

## Notes

- Add new issues from the review with the next id. Add rows to Goal, Scope, Approach, Steps and Progress.
- When the steps for an issue are done, update `documentation/application-architecture.md` in the same change. Follow
  `.github/skills/application-architecture/SKILL.md`. Remove each reference to the issue, for example
  `tasks/fix-architecture-review-issues.md` (I3), and describe the new behavior. Section 6 and the issue's flows can
  also need an update.
- When all issues are done, review `tasks/restart-paa-vegne-av-from-new-applications.md`. Reason: the fixes can
  change the flows, the error handling and the locks that the task depends on.
- Do the steps for one issue in order. The steps for different issues are independent, except for I6. I6 depends
  on I4 and I5.

## Progress

Replace ⬜ with ✅ when a step is done.

| Step | Task                                          | Status |
|------|-----------------------------------------------|--------|
| 1    | I1: Add `ErrorHandlingDeserializer`           | ⬜      |
| 2    | I1: Make `DeserializationException` retryable | ⬜      |
| 3    | I1: Add integration test                      | ⬜      |
| 4    | I2: Confirm deduplication with PAW            | ⬜      |
| 5    | I2: Use name-based Bekreftelse id             | ⬜      |
| 6    | I2: Add test                                  | ⬜      |
| 7    | I3: Wait for ack of `StartStoppMelding`       | ⬜      |
| 8    | I3: Add test                                  | ⬜      |
| 9    | I4: Make Flow F id update transactional       | ⬜      |
| 10   | I4: Add tests                                 | ⬜      |
| 11   | I5: Confirm grace replacement with PAW        | ⬜      |
| 12   | I5: Send `Start` from `oppdater-tom`          | ⬜      |
| 13   | I5: Add tests                                 | ⬜      |
| 14   | I6: Add advisory lock method                  | ⬜      |
| 15   | I6: Take lock in each flow                    | ⬜      |
| 16   | I6: Add concurrency tests                     | ⬜      |
| 17   | I7: Remove redundant Flow C filter            | ⬜      |
| 18   | I8: Move `v21` SQL to `V24`                   | ⬜      |
| 19   | I8: Verify migration in dev                   | ⬜      |
| 20   | I9: Remove `@EnableScheduling`                | ⬜      |
| 21   | Verify architecture document is updated       | ⬜      |
| 22   | Deploy to dev                                 | ⬜      |
