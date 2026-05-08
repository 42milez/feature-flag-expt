---
status: "accepted"
decision-makers: ["Akihiro TAKASE"]
consulted: []
informed: []
---

# Use Spring Data JDBC instead of JPA/Hibernate

## Context and Problem Statement

The project needs a relational persistence layer for feature flags, evaluation reads,
and audit records.

Spring Boot supports multiple persistence options, including Spring Data JDBC,
Spring Data JPA with Hibernate, and jOOQ. Which option best fits a codebase that
prioritizes predictable feature-flag evaluation, simple relational persistence,
auditability, and operational clarity?

## Decision Drivers

- The persistence layer should be scoped to what the application needs — CRUD, evaluation
  reads, and audit writes — without growing into the central complexity of the codebase
- Persistence behavior must remain transparent and traceable; lazy loading and ORM session
  lifecycle must not leak into service or evaluation logic
- Domain objects are Java records and should remain immutable; persistence-specific
  concerns should stay in persistence-aware classes
- INSERT vs UPDATE behavior for application-assigned IDs should be explicit

## Considered Options

- Spring Data JDBC
- Spring Data JPA with Hibernate
- jOOQ

## Decision Outcome

Chosen option: **Spring Data JDBC**, because it fits the project's access patterns —
straightforward CRUD, evaluation reads, and audit writes — and keeps persistence behavior
predictable and easy to reason about.

Spring Data JDBC is sufficient for that shape: it keeps repository usage familiar while
making persistence behavior simpler to reason about.

The main accepted cost is that entities with application-assigned IDs must implement
`Persistable<T>` so that `CrudRepository.save()` can distinguish INSERT from UPDATE.
This is small, explicit, and visible in the entity class.

### Consequences

- Good: persistence behavior is transparent — repository operations have direct,
  traceable database effects, with no lazy loading, proxy behavior, or ORM session
  lifecycle to reason about.
- Good: domain records such as `FeatureFlag` stay immutable; `FeatureFlagEntity` is the
  persistence-aware representation.
- Good: integration tests can exercise repository behavior without managing an
  EntityManager, Hibernate session, or lazy-loading proxy behavior.
- Bad: `Persistable<String>` must be implemented manually. `FeatureFlagEntity` carries a
  `@Transient boolean newEntity` flag and constructor paths for new vs loaded entities.
- Neutral: a separate entity class exists alongside each domain record, adding a thin
  conversion layer in the service.
- Neutral: if the project later needs complex SQL queries, jOOQ or custom JDBC queries may
  be reconsidered for those specific use cases.

### Confirmation

- `spring-boot-starter-data-jdbc` is present in the build.
- `spring-boot-starter-data-jpa` is absent from `gradle/libs.versions.toml` and all
  `build.gradle.kts` files; Hibernate is not on the classpath.
- `FeatureFlagRepository` extends Spring Data `CrudRepository`.
- `FeatureFlagEntity implements Persistable<String>` with explicit `isNew()` behavior.

## Pros and Cons of the Options

### Spring Data JDBC

- Good: familiar Spring Data repository model without an ORM session
- Good: simpler persistence behavior for CRUD-oriented relational tables
- Good: no lazy loading, dirty checking, or proxy behavior to account for in services or tests
- Bad: requires `Persistable<T>` for entities with application-assigned IDs
- Bad: less convenient than JPA/Hibernate for rich relationship-heavy domain models

### Spring Data JPA with Hibernate

- Good: mainstream Spring Boot choice with a large ecosystem
- Good: useful for applications with rich entity relationships and ORM-friendly data access
- Good: dirty checking and lazy loading can reduce boilerplate in relationship-heavy models
- Bad: JPA entities typically require ORM-specific construction and mutation rules,
  which would either add a separate persistence model or weaken the preference for
  immutable Java records at the domain boundary
- Bad: persistence context, proxy behavior, lazy loading, and dirty checking add lifecycle
  complexity that is unnecessary for the current feature set
- Bad: lazy loading can introduce runtime surprises outside an active transaction

### jOOQ

- Good: excellent when type-safe, SQL-first data access is the primary design goal
- Good: makes SQL shape highly explicit
- Bad: requires a code-generation step and additional setup
- Bad: more SQL modeling surface than the current CRUD and audit persistence needs

## More Information

- [`gradle/libs.versions.toml`](../../gradle/libs.versions.toml)
- [`service/build.gradle.kts`](../../service/build.gradle.kts)
- [`FeatureFlagEntity.java`](../../service/src/main/java/com/github/milez42/featureflags/flags/FeatureFlagEntity.java)
- [Spring Data JDBC reference](https://docs.spring.io/spring-data/relational/reference/jdbc.html)
