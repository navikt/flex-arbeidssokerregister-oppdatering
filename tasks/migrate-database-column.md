# Rename `arbeidssokerperiode_id` to `arbeidssokerregisterperiode_id`

## Problem

The column `arbeidssokerperiode.arbeidssokerperiode_id` holds the id of a registry period in Arbeidssøkerregisteret.
The field `Arbeidssokerperiode.arbeidssokerperiodeId` holds the same id.
The entity `Arbeidssokerperiode` represents a vedtaksperiode from `sykepengesoknad-backend`.
The name of the column and the field is therefore ambiguous.

## Goal

- Column: `arbeidssokerperiode.arbeidssokerregisterperiode_id`.""
- Field: `Arbeidssokerperiode.arbeidssokerregisterperiodeId`.
- The application must respond to traffic during the full migration.

## Scope

- In scope: the column in the table `arbeidssokerperiode`.
- In scope: the entity field and the repository finders.
- In scope: the index from `V7__legg_til_index_arbeidssokerperiode_id.sql`.
- Out of scope: `periodebekreftelse.arbeidssokerperiode_id`. It is a foreign key to `arbeidssokerperiode.id`.
  It does not hold a registry period id. Do not rename it in this task.
- Out of scope: JSON field names in the `FlexInternalController` request and response classes. They are an API
  contract. Change them only after agreement with the API consumers.

## Approach

Use expand and contract. Each phase is a separate deploy.
Nais does a rolling deploy. Old and new pods run at the same time.
Flyway runs before the new pods start.
Each deploy must work with the schema of the previous phase and of the next phase.

## Steps

1. Create a Flyway migration that adds the nullable column `arbeidssokerregisterperiode_id` with an index.
2. Deploy the application. Expected result: the new column and the new index exist.
3. Add the field `Arbeidssokerperiode.arbeidssokerregisterperiodeId`.
4. Write the same value to both fields on every insert and update. Do not change the reads.
5. Deploy the application. Expected result: new and updated rows have the same value in both columns.
6. Create a Flyway migration that copies `arbeidssokerperiode_id` to `arbeidssokerregisterperiode_id` where the values
   are different. Pods from before step 5 write only to the old column, so do this step after step 5.
7. Deploy the application. Expected result: no rows have different values in the two columns.
8. Change all reads to the new column. For example, change `ArbeidssokerperiodeRepository.findByArbeidssokerperiodeId`
   to `findByArbeidssokerregisterperiodeId`. Do not change the writes.
9. Deploy the application. Expected result: Flow A, D, E and F in `documentation/application-architecture.md` work
   as before.
10. Remove the field `Arbeidssokerperiode.arbeidssokerperiodeId`. The application then stops writes to the old column.
11. Deploy the application. Expected result: the application runs without errors. Inserts work, because the old
    column is nullable.
12. Create a Flyway migration that drops the index `arbeidssokerperiode_arbeidssokerperiode_id_idx` and the column
    `arbeidssokerperiode_id`. Pods from step 9 write to the old column, so do this step after step 11.
13. Deploy the application. Expected result: the old column and the old index do not exist. The application runs
    without errors.
14. Update the tests, the test data and `documentation/application-architecture.md` to use the new name.

## Notes

- Use the next free Flyway version for each migration (`V24` and up).
- Use an uppercase `V` in the file name. Flyway does not run files with a lowercase `v`.
- Do not change existing migrations. Flyway validates the checksums at startup.
- To roll back, deploy the previous phase again. This works until step 13, because both columns exist until then.

## Progress

Replace ⬜ with ✅ when a step is done.

| Step | Task                                                | Status |
|------|-----------------------------------------------------|--------|
| 1    | Migration: add the new column and index             | ⬜      |
| 2    | Deploy: the new column and index exist              | ⬜      |
| 3    | Add the new field                                   | ⬜      |
| 4    | Write to both columns                               | ⬜      |
| 5    | Deploy: rows have the same value in both columns    | ⬜      |
| 6    | Migration: copy old values to the new column        | ⬜      |
| 7    | Deploy: all values are the same                     | ⬜      |
| 8    | Read from the new column                            | ⬜      |
| 9    | Deploy: the flows work as before                    | ⬜      |
| 10   | Remove the old field                                | ⬜      |
| 11   | Deploy: the application runs without errors         | ⬜      |
| 12   | Migration: drop the old index and column            | ⬜      |
| 13   | Deploy: the old column and index do not exist       | ⬜      |
| 14   | Update the tests, the test data and the docs        | ⬜      |
