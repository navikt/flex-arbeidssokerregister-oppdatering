# Restart PaaVegneAv from new applications

## Problem

`ArbeidssokerperiodeService.restartArbeidssokerperiode` sends PaaVegneAv `Start` when it forwards a registry `START` to `sykepengesoknad-backend`.
`SykepengesoknadService.behandleVedtaksperiode` ignores FREMTIDIG applications for an existing vedtaksperiode.
New applications therefore cannot restart responsibility after a `BRUKER` stop, although registry restarts start responsibility before recreated applications arrive.
The restart also uses the full grace duration without accounting for time since the original vedtaksperiode.

## Goal

- An unprocessed FTA application with status `FREMTIDIG` and `ignorerArbeidssokerregister != true` must trigger the restart decision.
- New applications must restart responsibility after `BRUKER` or a registry end, but not after `AVSLUTTET_PERIODE` or `UTGAATT`.
- Registry `START` forwarding must keep its current guards, payload and timing, without sending PaaVegneAv `Start`.
- A restart must use exactly one current, active registry period confirmed through `ArbeidssokerregisterClient`.
- An application restart must wait until the stored registry id matches the current registry id.
- A processed FREMTIDIG application id must not trigger another restart, including after a later stop.
- A restart must use the remaining grace allowance before the fixed deadline, without extending that deadline.
- First-time starts and SENDT correction handling must keep their current behavior.
- Database state and application-id tracking must commit only after a required PaaVegneAv send succeeds.

## Scope

- In scope: `SykepengesoknadService.behandleVedtaksperiode`, `sendPaaVegneAvStartMelding` and restart grace calculation.
- In scope: `ArbeidssokerperiodeService.restartArbeidssokerperiode` and the state shared by registry events and application processing.
- In scope: `Arbeidssokerperiode`, its repository, new Flyway migrations and durable FREMTIDIG application-id tracking.
- In scope: state updates on existing Start/Stopp paths, including `FlexInternalController`, without changing their trigger conditions.
- In scope: `TestdataResetListener`, `FellesTestOppsett`, affected tests and `documentation/application-architecture.md`.
- Out of scope: changes to registry `START` forwarding eligibility, including its latest-period and four-month guards. The existing forwarding behavior must remain intact.
- Out of scope: restarts after `AVSLUTTET_PERIODE` or `UTGAATT`. These remain terminal stop reasons.
- Out of scope: changes to SENDT confirmation decisions or correction filtering. This task changes FREMTIDIG processing.
- Out of scope: historical Kafka replay, historical application-id backfill and restarts without a new application. Tracking starts at deployment.
- Out of scope: Kafka topics, keys, message formats, HTTP contracts, dependencies and NAIS configuration. The existing integrations are sufficient.
- Out of scope: general Kafka delivery guarantees and unrelated concurrency fixes. Preserve the existing listener and producer behavior.

## Approach

Make eligible FREMTIDIG applications own both first-time and restarted PaaVegneAv `Start` decisions.
Keep registry lifecycle processing responsible for registry state and `START` forwarding, but separate that state from active PaaVegneAv responsibility.
Store processed FREMTIDIG application ids in the same transaction as the decision, including decisions that send no message.
Calculate restart grace against a fixed deadline so that a late restart does not grant a new four-month allowance.
Defer an application when registry ids differ, so that application processing cannot suppress registry `START` forwarding.

## Steps

1. Extend persistence for responsibility state and processed application ids.
   `Arbeidssokerperiode.paaVegneAvAktiv` must distinguish active responsibility from a registry period awaiting an application.
   Existing rows must derive this state from `sendtPaaVegneAv`, `sendtAvsluttet` and `avsluttetMottatt`, without changing timestamps.
   `BehandletFremtidigSoknadRepository` must enforce unique application ids and reference `Arbeidssokerperiode.id`.
   Existing Start/Stopp paths must maintain the state without competing registry and application updates overwriting it.
   `TestdataResetListener` and `FellesTestOppsett.slettFraDatabase` must delete the new child rows before their parent.
2. Change `ArbeidssokerperiodeService.restartArbeidssokerperiode` to prepare the registry link without starting responsibility.
   `finnRestartbarArbeidssokerperiode`, its guards and `StartStoppMelding` behavior must remain unchanged.
   The flow must update the registry id and clear `avsluttetMottatt` and `avsluttetTidspunkt`.
   Responsibility must remain inactive, without a PaaVegneAv `Start` send or a change to `sendtPaaVegneAv`.
3. Extend `SykepengesoknadService.behandleVedtaksperiode` to handle existing rows.
   The restart decision must follow the eligibility, registry-validation and ordering rules in Goal.
   Tracking must include active-period and terminal-stop no-ops, but must roll back on deferral, validation failure or send failure.
   A successful restart must mark responsibility active, update `sendtPaaVegneAv`, and clear the paired `BRUKER` stop fields.
   The existing row id, vedtaksperiode, Kafka-key and confirmation history must remain intact.
   First-time creation and its validations must remain unchanged.
4. Add a restart-specific grace calculation beside `beregnGraceMS`.
   The deadline must equal `vedtaksperiodeTom.plusDays(1).plusMonths(4)` at the start of day in `Europe/Oslo`.
   The calculation must return `graceMS = max(0, deadline - restartTime - 14 days)` in milliseconds.
   The calculation and persisted start timestamp must use one captured restart time.
   Restarts at or after the deadline must send nothing.
   `beregnGraceMS` and grace behavior for first-time and manual starts must remain unchanged.
5. Add focused regression cases to `VedtaksperiodeIntegrationTest`, `ArbeidssokerperiodeServiceIntegrationTest` and `BeregnGraceMSTest`.
   Cases must cover `BRUKER`, registry-end preparation, recreated applications and new applications produced after a correction.
   Cases must preserve forwarding, first-time starts, active-period no-ops, terminal stops and ignored applications.
   Replay cases must include the same id after another stop, a different id after another stop, and no-op tracking.
   Ordering cases must cover both topic orders, deferred retry, invalid registry state and rollback without consuming the application id.
   Persistence cases must cover competing registry/application updates and child-row cleanup.
   Grace cases must cover late restarts, deadline boundaries, zero grace, month boundaries and daylight-saving transitions.
6. Update the affected sections of `documentation/application-architecture.md`.
   Flows A and D, duplicate handling, persistence, field ownership, transaction details and the glossary must describe the new flow.
   Invariant 24 must separate registry-link updates from application-triggered PaaVegneAv starts.
   The one-row-per-vedtaksperiode, correction-filtering, message-key and transaction invariants must remain intact.
   The document must describe application-id tracking and the unchanged production/non-production retry difference.
7. Verify the implementation with the affected tests and `./gradlew ktlintCheck`.
   Follow `.github/instructions/tests.instructions.md` for runner selection and Docker access.
   Expected result: restart, replay, ordering, grace and cleanup cases pass; first-time starts and SENDT correction handling remain unchanged.
8. Deploy the application through the existing deployment process.
   Expected result: migrations complete, registry `START` forwarding continues, and new eligible applications start responsibility with the remaining grace allowance.

## Notes

- Use the glossary in `documentation/application-architecture.md` for existing project terms.
- Active responsibility means this service has sent PaaVegneAv `Start` for the current registry period and has not stopped responsibility.
- A processed FREMTIDIG application is an eligible application whose handling committed, including a decision that sent no message.
- A registry end sets `avsluttetMottatt`; it is not `AvsluttetAarsak.AVSLUTTET_PERIODE`.
- `AVSLUTTET_PERIODE` means this service sent PaaVegneAv `Stopp` after the final application.
- Four calendar months differs from 12 weeks. Use calendar months for the agreed deadline.
- The Avro `Start` schema defines grace as an allowance after `intervalMS`, used for monitoring rather than functional termination.
- Only restart grace uses the remaining allowance. First-time starts retain the existing full-duration calculation.
- Track ids from deployment onward. An older, previously untracked application can trigger its first restart after deployment.
- Do not commit the id of a deferred application. Production retries the record; non-production logs and skips it under existing listener behavior.
- A differing registry id waits for the unchanged forwarding flow. Do not bypass its guards to resolve a deferred record.
- A record can remain deferred if the forwarding guards reject the registry start. In production, that record blocks its partition.
- Kafka sends and database commits are not atomic. Preserve the existing acknowledged-send boundary without claiming exactly-once delivery.
- The user approved database migrations for durable application-id tracking.

## Progress

Replace ⬜ with ✅ when a step is done.

| Step | Task                                      | Status |
|------|-------------------------------------------|--------|
| 1    | Persist responsibility and processed ids   | ⬜      |
| 2    | Separate registry restart from Start       | ⬜      |
| 3    | Restart responsibility from applications   | ⬜      |
| 4    | Calculate remaining restart grace          | ⬜      |
| 5    | Add focused regression cases               | ⬜      |
| 6    | Update application architecture            | ⬜      |
| 7    | Verify affected behavior                   | ⬜      |
| 8    | Deploy the application                     | ⬜      |
