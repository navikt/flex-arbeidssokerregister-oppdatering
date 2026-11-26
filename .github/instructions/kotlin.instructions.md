---
applyTo: "**/*.kt"
---

# Kotlin

ktlint controls formatting. This file does not give formatting rules.

- Apply these rules to new and changed code. Do not refactor unrelated code.

## Immutability and null safety

- Use `val`. Use `var` only for local state that must change inside a function.
- Use read-only types (`List`, `Map`, `Set`) in signatures and properties.
- Use `copy()` to change a data class instance.
- Do not use `!!`. Use a safe call, or `?:` with `return`, `throw` or a default value.
  Reason: `!!` throws a `NullPointerException` without context.
- When a missing value stops the flow, throw a domain exception with the relevant IDs in the message.
- Make a type nullable only when null has a meaning. Do not give a nullable type a non-null default.
- Use `findByIdOrNull()` from Spring Data. Do not use `findById().orElse(null)`.

## Data modeling and expressions

- Use data classes with `val` properties for entities, DTOs and Kafka messages.
- Use an enum class for a fixed set of values. Use a sealed class or sealed interface when the variants carry different data.
- Do not add an `else` branch to a `when` on an enum or sealed type.
  Reason: the compiler then reports cases that are not handled.
- Use `when` instead of an `if`-`else` chain with more than two branches.
- Use expression bodies for functions that return one expression.
- Use early `return` for guard clauses. Keep the main flow flat.
- Use standard library collection functions (`filter`, `map`, `singleOrNull`) instead of loops that build collections.
- Use `single()` or `singleOrNull()` to get the only element. Do not check the size and then use `first()` or an index.
- Do not call `toList()` on an `Iterable` before `single()`, `map` or `filter`. These functions accept an `Iterable`.
- Put mapping between types in extension functions, for example `Arbeidssokerperiode.tilPaaVegneAvStartMelding()`.

## Spring and Java habits

- Use constructor injection with `private val`. Do not use `lateinit` or `@Autowired` for dependencies.
- Use properties. Do not write getter and setter methods.
- Use top-level functions and extension functions. Do not write static utility classes.

## Readability and comments

- Use scope functions (`let`, `also`, `apply`, `run`) only when they make the code clearer. Use a local `val` or `if`-`else` for long blocks.
- Do not use `?.let { } ?: run { }`. Use `if`-`else`.
- Use private extension functions with clear names for domain conditions, for example `SykepengesoknadDTO.erSendtFriskTilArbeidSoknad()`.
- Write comments and KDoc that explain why, not what. Follow `documentation/writing-style.md`.
