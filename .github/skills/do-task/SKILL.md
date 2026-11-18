---
name: do-task
description: Does one step from a task file in `tasks/`. Verifies the step and updates the progress table. Use when
  the user asks to do, continue or pick up a step in a task file.
disable-model-invocation: true
---

# Do task

The user gives a task file in `tasks/` and a step number.
You do only that step.
You verify the step and mark it as done.

## Input

1. If the user gives no task file, list the files in `tasks/`.
2. Ask the user to select a file with the `ask_user` tool.
3. If the user gives no step number, select the first step with ⬜ in the progress table.

## Preparation

1. Read `documentation/writing-style.md`. Apply its rules to all text that you write.
2. Read the full task file.
3. Read the Goal, Scope, Approach and Notes. They apply to all steps.
4. Read the files in `.github/instructions/` that apply to the files that the step changes.
5. If the step changes the dataflow, read `documentation/application-architecture.md`.
6. Find the code that the step names.
7. Compare the facts in the task file with the code.

## Checks

Ask the user with the `ask_user` tool before you start if one of these conditions is true:

- The step has ✅.
- An earlier step has ⬜, and the order of the steps is important. The step text and the Approach give the order.
- A fact in the task file is not correct for the code.
- The step needs a change that is out of scope.
- More than one good approach exists, and the task file does not select one.

Do not guess. A wrong guess gives a wrong change.

## Work

1. Do the action in the selected step. Do not do the next step.
   Reason: each step can be a separate change or deploy.
2. Follow the Approach and the Notes.
3. Do not change code that the step does not name.
4. Record other problems that you find. Give them to the user in the summary.
5. If the step is a deploy step, do not deploy. Tell the user the expected result.
6. Ask the user to confirm the expected result after the deploy.
7. If the step is an investigation step, write the result in the file that the step names.
   If the step names no file, write the result in the Notes of the task file.

## Verification

1. Run the smallest test command that covers the change. The commands are in
   `.github/instructions/tests.instructions.md`.
2. If the step has `Expected result:`, verify the expected result.
3. If the verification fails, correct the change.
4. If you cannot correct the change, stop. Tell the user the cause of the failure.

The step must pass the verification before you mark it as done.

## Progress

1. Replace ⬜ with ✅ for the step in the progress table.
2. If the plan is not correct, update the task file.
3. Tell the user about each change to the plan.
4. Do not commit. The user decides when to commit.

## Summary

Tell the user these facts:

- The step that you did.
- The files that you changed.
- The result of the verification.
- The next step in the task file.
- The problems that you found outside the step.
