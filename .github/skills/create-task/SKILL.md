---
name: create-task
description: Creates a task file in `tasks/` from a problem that the user gives. The task file is a short plan that
  humans and AI agents can do. Use when the user asks for a new task file or a plan for a task.
disable-model-invocation: true
---

# Create task

The user gives a problem. You write a task file in `tasks/` that plans the solution.
Do not change code in this skill.

## Preparation

1. Read `documentation/writing-style.md`. Apply its rules to all text in the task file.
2. Read `tasks/TEMPLATE.md`. The task file must follow its sections and rules.
3. Read the problem from the user.
4. Find the code, schema and configuration that the problem is about.
5. If the problem changes the dataflow, read `documentation/application-architecture.md`. Identify the invariants
   that the task can affect.

## Clarification

Ask the user when something is unclear. Do not guess. A wrong guess gives a wrong plan.

1. Make a list of the open questions. Examples:
   - The goal or a constraint is not clear.
   - A part can be in scope or out of scope.
   - There is more than one good approach.
   - The task breaks an invariant in `documentation/application-architecture.md`.
2. Ask all questions in one form with the `ask_user` tool. Give a recommended answer as the default when you have one.
3. Repeat until no questions are open.

## Writing

1. Select a file name in agreement with `tasks/TEMPLATE.md`.
2. If the file exists, ask the user if you can replace it.
3. Write the task file.
4. Compare the task file with `tasks/TEMPLATE.md` and `documentation/writing-style.md`. Correct all differences.

## Summary

Tell the user the path of the task file. Give the approach in one or two sentences.
List the decisions that you made without a question to the user.
