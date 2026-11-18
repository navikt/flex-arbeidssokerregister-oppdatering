# Task template

Use this template for all task files in `tasks/`.
A task file is a short plan. Humans and AI agents must both be able to do the task from it.
Write all text in agreement with `documentation/writing-style.md`.

## Rules

- Give the file a kebab-case name that tells the task, for example `migrate-database-column.md`.
- Use the sections below, in the same order. Do not add other sections.
- Keep the steps at a high level. Name the class, field or method to change. Do not list each code change.
- Refer to code by class and member name, for example `ArbeidssokerperiodeRepository.findByFnr`. Do not use line
  numbers.
- Write the plan only. Do not change code when you write a task file.

## Sections

Copy the block below into the new file. Replace the text in angle brackets. Remove the `Notes` section if it is empty.

```markdown
# <Short title in the imperative, for example "Rename `x` to `y`">

## Problem

<What is wrong or missing now. Give the facts. Use 2 to 5 sentences.>

## Goal

- <One result that must be true when the task is done.>
- <Add one line for each result. Include constraints, for example "The application must respond to traffic.">

## Scope

- In scope: <One part of the code, schema or configuration that the task changes.>
- Out of scope: <One related part that the task does not change.> <The reason, in one sentence.>

## Approach

<The strategy, in 2 to 5 sentences. Give the reason for the strategy, for example a constraint from the Goal.>

## Steps

1. <One action, in the imperative.>
2. Deploy the application. Expected result: <What must be true after the step.>
3. <Add the reason in one sentence if the order of the steps is important.>

## Notes

- <One fact, risk or rule that applies to more than one step, for example how to roll back.>

## Progress

Replace ⬜ with ✅ when a step is done.

| Step | Task                          | Status |
|------|-------------------------------|--------|
| 1    | <Short name of step 1>        | ⬜      |
| 2    | <Short name of step 2>        | ⬜      |
```

## Section rules

| Section  | Rule                                                                                         |
|----------|----------------------------------------------------------------------------------------------|
| Problem  | Describe the current state. Do not describe the solution.                                    |
| Goal     | Write each result so that it is possible to verify it.                                       |
| Scope    | Name each part that a reader can think is in scope, but is not.                              |
| Approach | Describe the strategy. Do not repeat the steps.                                              |
| Steps    | Write one action for each step. Add `Expected result:` to each deploy or verification step.  |
| Notes    | Write facts that do not belong to one step.                                                  |
| Progress | Add one row for each step, with the same number. Use a short name of at most 8 words.        |
