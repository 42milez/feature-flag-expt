# Feature Flag Experiment Constitution

## Core Principles

### I. Project Instructions Are Binding

Every specification, plan, and implementation task MUST follow `AGENTS.md` and
`CLAUDE.md`. `CLAUDE.md` is the primary project instruction source unless
`AGENTS.md` explicitly overrides it. All written artifacts, including specs,
plans, documentation, code comments, and task descriptions, MUST be written in
English.

### II. API Responses Use Kotlin DTO Boundaries

API responses MUST NOT expose Java domain models, validation result records, or
persistence entities directly. When controller or advice code generates a
response body, it MUST convert the Java-side model into a Kotlin `*Response` DTO
at the API boundary. OpenAPI response schemas MUST reference the public response
DTO, not the internal Java model.

Example:

```kotlin
val result = validator.validate(current, proposed, context)

return RolloutPolicyValidationResponse(
    flagKey = result.flagKey(),
    allowed = result.allowed(),
    violations = result.violations(),
)
```

### III. Code-First API Contracts Stay Tested

Public API changes MUST keep the Spring MVC, Bean Validation, and springdoc
annotations aligned with runtime behavior. Any API response, request, validation,
or schema change MUST include tests that verify both the runtime JSON shape and
the OpenAPI contract when the schema is affected.

### IV. Domain, Persistence, and API Models Stay Separate

Feature flag evaluation logic MUST depend on immutable, persistence-agnostic
domain values. Spring Data JDBC entities MUST remain persistence-boundary types.
API request and response DTOs MUST remain API-boundary types. Conversions should
be explicit and close to the boundary that owns the mapping.

### V. Quality Gates Are Required

Implementation tasks that change files outside `docs/` MUST finish by running,
in order, `./gradlew :service:spotlessCheck`, `./gradlew :service:compileJava`,
and `./gradlew :service:test`. Documentation-only changes under `docs/` may skip
these checks.

## Technology Constraints

This project uses Spring Boot 4.x and Jackson 3.x. Code MUST use
`tools.jackson.*` imports and MUST NOT use `com.fasterxml.jackson.*` imports.
The persistence layer uses Spring Data JDBC, not JPA or Hibernate; specs and
plans MUST NOT describe repository behavior as JPA-managed or Hibernate-backed.

Kotlin DTOs that participate in Bean Validation or OpenAPI schema generation
MUST use the correct annotation targets, such as `@field:Schema` and
`@field:Size`, so runtime validation and generated schemas inspect the intended
properties.

## Development Workflow

Spec Kit specs MUST call out API boundary effects when a feature touches
controllers, exception handlers, request DTOs, response DTOs, or OpenAPI
annotations. Implementation plans MUST include a Constitution Check that
explicitly confirms API response DTO mapping, Java/Kotlin boundary ownership,
OpenAPI schema impact, and required tests.

Tasks generated from a plan MUST include concrete file paths and must keep
runtime behavior, OpenAPI snapshots, and tests in sync. If a task introduces or
discovers a non-obvious project rule, update `CLAUDE.md` under the appropriate
coding rule category.

## Governance

This constitution governs Spec Kit-driven design and implementation in this
repository. Amendments require updating this file, preserving compatibility with
`AGENTS.md` and `CLAUDE.md`, and reviewing templates that reference
constitutional gates. Pull requests and implementation reviews MUST reject API
response changes that bypass Kotlin response DTOs with Java domain or result
models.

**Version**: 1.0.0 | **Ratified**: 2026-05-17 | **Last Amended**: 2026-05-17
