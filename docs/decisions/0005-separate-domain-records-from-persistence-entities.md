---
status: "accepted"
date: 2026-05-10
decision-makers: ["Akihiro TAKASE"]
consulted: []
informed: []
---

# Separate Domain Records from Persistence Entities

## Context and Problem Statement

Feature flags are used in two different contexts:

* evaluation logic, where a flag should behave like an immutable value object with only
  domain-relevant fields
* relational persistence, where Spring Data JDBC needs mapping metadata, child collection
  mapping, and explicit INSERT vs UPDATE semantics for the application-assigned flag key

In many Spring projects, a single persistence entity is also used as the domain object.
That would make the model smaller, but it would also place Spring Data JDBC concerns on the
object passed into feature-flag evaluation.

Should the project keep `FeatureFlag` as a separate immutable domain record and use
`FeatureFlagEntity` only at the persistence boundary, or collapse them into one class?

## Decision Drivers

* Evaluation logic should depend on an immutable, persistence-agnostic representation of a
  feature flag.
* Spring Data JDBC mapping details should stay in persistence-aware classes, not in the
  model consumed by `FeatureFlagEvaluator`.
* `CrudRepository.save()` must be able to distinguish INSERT from UPDATE for
  application-assigned flag keys.
* Child table mapping for target environments and tenant allowlists should not leak into
  the evaluation model.

## Considered Options

* Separate domain record and persistence entity (chosen)
* Single Spring Data JDBC entity used everywhere

JPA/Hibernate-style entities are not reconsidered here because ADR-0002 already selects
Spring Data JDBC and rejects JPA/Hibernate for this project.

## Decision Outcome

Chosen option: **separate domain record and persistence entity**, because it keeps the
feature-flag evaluation model small, immutable, and independent of relational persistence
while still giving Spring Data JDBC the structure it needs.

`FeatureFlag` is the domain-facing value type. It is a Java record, validates required
fields and value ranges, copies collections defensively, and has no Spring persistence
annotations or `Persistable` behavior. `FeatureFlagEvaluator` receives this type, so
evaluation rules do not need to know about database table names, child table entities, or
persistence state.

`FeatureFlagEntity` is the persistence-aware aggregate root. It maps to the
`feature_flags` table, owns the mapped child collections for target environments and tenant
allowlists, and implements `Persistable<String>`. The static `create()` factory marks a
new entity with `newEntity = true`, while the `@PersistenceCreator` constructor marks
loaded entities as existing. This lets Spring Data JDBC choose INSERT for newly-created
flags and UPDATE for loaded or reconstructed existing flags.

Conversion currently lives in `FeatureFlagService`, close to the repository boundary. That
is acceptable while the mapping remains simple and local to the feature-flag service.

### Consequences

* Good, because `FeatureFlagEvaluator` is insulated from Spring Data JDBC annotations,
  `Persistable<String>`, and child table entity types.
* Good, because the domain model remains immutable and value-oriented; validation and defensive
  collection copying happen in the `FeatureFlag` canonical constructor.
* Good, because INSERT vs UPDATE behavior is explicit through `FeatureFlagEntity.create()`,
  `@PersistenceCreator`, and `isNew()`.
* Good, because persistence can evolve independently, for example by changing child table mapping,
  without changing the evaluator's input type.
* Bad, because the service layer carries conversion code between `FeatureFlagEntity`,
  `FeatureFlag`, and API response types such as `FeatureFlagResponse`.
* Bad, because future domain fields must be added in more than one place, so reviews and tests must
  verify that mappings stay complete.
* Neutral, because if mapping grows beyond simple field and collection conversion, a dedicated
  mapper may be introduced later.

### Confirmation

* `FeatureFlag` is a Java record with validation and defensive collection copying.
* `FeatureFlagEntity` is a Spring Data JDBC aggregate root annotated with `@Table` and
  implements `Persistable<String>`.
* `FeatureFlagEntity.create()` creates new instances with `isNew() == true`; the
  `@PersistenceCreator` constructor creates loaded instances with `isNew() == false`.
* `FeatureFlagService.toDomain()` converts `FeatureFlagEntity` into `FeatureFlag` before
  calling `FeatureFlagEvaluator`.
* Repository integration tests cover creating a new flag and saving an existing flag with
  replaced child collections.

## Pros and Cons of the Options

### Separate Domain Record and Persistence Entity

* Good, because keeps evaluator input persistence-agnostic
* Good, because keeps `Persistable<String>` state out of the domain record
* Bad, because requires explicit conversion code

### Single Spring Data JDBC Entity Used Everywhere

* Good, because fewer classes and less conversion code
* Good, because API, service, evaluator, and repository code would all share one type
* Bad, because evaluation logic would depend on Spring Data JDBC-specific mapping and lifecycle
  concerns
* Bad, because the evaluator would see persistence child entity types instead of plain domain
  values
* Bad, because persistence-oriented constructors and `isNew()` state would become part of the
  domain-facing model

## More Information

* [`FeatureFlag.java`](../../service/src/main/java/com/github/milez42/featureflags/flags/FeatureFlag.java)
* [`FeatureFlagEntity.java`](../../service/src/main/java/com/github/milez42/featureflags/flags/FeatureFlagEntity.java)
* [`FeatureFlagService.java`](../../service/src/main/java/com/github/milez42/featureflags/flags/FeatureFlagService.java)
* [`FeatureFlagEvaluator.java`](../../service/src/main/java/com/github/milez42/featureflags/flags/FeatureFlagEvaluator.java)
* [`FeatureFlagRepositoryIntegrationTest.java`](../../service/src/test/java/com/github/milez42/featureflags/flags/FeatureFlagRepositoryIntegrationTest.java)
* [ADR-0002: Use Spring Data JDBC instead of JPA/Hibernate](0002-use-spring-data-jdbc-instead-of-jpa.md)
