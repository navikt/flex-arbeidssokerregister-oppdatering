---
name: application-architecture
description: The overall application architecture. How data flows through the 
  application (Kafka input and output, outbound API calls, database persistence) 
  and the invariants that must hold. Use when planning or making changes to 
  business logic, data models, services, or how data moves between components.
---

# Application Architecture

`documentation/application-architecture.md` describes the overall application 
architecture and the dataflow of the application. It is the source of truth for 
how data moves through the system.

## Preparation

Do these steps before any code change.

1. Read `documentation/application-architecture.md`.
2. Identify the flows and components that the task changes.
3. Identify the invariants that the change can affect.
4. Keep these invariants intact. Change an invariant only when the task
   explicitly says so.
5. If the task breaks an invariant without saying so, stop. Tell the user
   which invariant is affected. Ask how to continue.

## Implementation

Apply these rules during the code change.

- Follow the flows and layers that the document describes.
- Do not add a new path between components without a reason. Give the
  reason in the summary.
- If the code and the document do not agree, do not choose one. Tell the
  user about the difference. Ask which one is correct.

## Documentation update

Do these steps after the code change.

1. Compare the change with the document. The dataflow has changed if one of
   these is true:
   - A component, service or data model is added, removed or renamed.
   - Data moves along a different path or in a different order.
   - An invariant, a validation point or the owner of data changes.
   - An external input or output is added or changed (Kafka topic, API,
     database).
2. If the dataflow has changed, update `documentation/application-architecture.md` in the
   same change. An edit in the diff gets reviewed. A suggestion in chat
   gets lost.
3. Change only the affected sections. Keep the existing structure.
4. Describe the current state. Do not write a change log.
5. Update the diagram if it does not match the text.
6. Add new project-specific terms to the Glossary.
7. If the dataflow has not changed, do not edit the document.

## Writing rules for the document

Read `documentation/writing-style.md` and follow it. Also apply these rules:

- Refer to code by class and method name, for example
  `OrderService.process`. Do not copy code or use line numbers.
- Refer to configuration by property key. Do not use values that change
  between environments.
- Keep each fact in one place. Refer to other sections instead of repeating
  details.

## Summary

End the reply with a "Result" note. Write one of these:
- The updated sections of `documentation/application-architecture.md`, and the reason for
  each update.
- "The change does not affect the documented application architecture."