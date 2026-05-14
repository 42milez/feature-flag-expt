---
status: "accepted"
date: 2026-05-09
decision-makers: ["Akihiro TAKASE"]
consulted: []
informed: []
---

# Represent Audit Event Details as a Sealed Interface with Records

## Context and Problem Statement

Each audit event type carries a different set of fields. For example, `FLAG_CREATED`
captures an initial snapshot of all flag fields, while `ROLLOUT_PERCENTAGE_CHANGED`
captures only the old and new integer values. The `details` column is stored as JSONB and
must round-trip through JSON serialization and deserialization.

The design must:

* represent each event type's payload as a distinct, strongly-typed structure
* prevent the addition of a new `AuditEventType` without also providing a deserialization
  path for it
* integrate with Jackson without requiring per-subtype serialization configuration

## Decision Drivers

* Adding a new event type should produce a compile error if the deserialization path is
  missing, not a runtime failure
* Each detail type is immutable and value-oriented; there is no behavior to attach beyond
  data
* The persistence layer deserializes from JSON via `AuditEventType` as the discriminator;
  a type-per-discriminator mapping is already available
* Jackson 3.x (auto-configured by Spring Boot 4.x) serializes records via their accessor
  methods without additional annotation

## Considered Options

* Sealed interface with record subtypes (chosen)
* Abstract class hierarchy with `@JsonTypeInfo` and a `type` discriminator field
* Single `Map<String, Object>` or untyped JSON node

## Decision Outcome

Chosen option: **sealed interface with record subtypes**, because it models each event
payload as a distinct, immutable value type and ties compile-time exhaustiveness checking
to the deserialization switch.

`AuditEventDetails` is a `sealed interface` whose only permitted subtypes are the record
classes defined inside it (e.g., `FlagCreatedDetails`, `RolloutPercentageChangedDetails`).
`AuditEventRepository.deserialize()` uses a `switch` expression over `AuditEventType` to
map each discriminator to the correct record class. Because the switch expression covers
all enum values with no `default` arm, the compiler rejects a missing case — new event
types cannot be added to `AuditEventType` without also adding a deserialize branch.

The main accepted cost is that deserialization is coupled to `AuditEventType`: the enum
and the switch must be updated together. This coupling is intentional — they represent the
same conceptual set and should evolve together.

### Consequences

* Good: adding a new `AuditEventType` value without updating the `switch` in
  `deserialize()` is a compile error, not a silent runtime failure. The compiler enforces
  that every enum value has a deserialize branch; it does not enforce that the chosen
  `AuditEventDetails` subtype semantically matches the `AuditEventType` — that pairing
  must be verified by review and tests.
* Good: each detail record is immutable and carries only the fields that are relevant to
  its event type, making the payload shape self-documenting.
* Good: Jackson 3.x handles records without annotation — serialization reads accessor
  methods; deserialization invokes the canonical constructor. No `@JsonCreator`,
  `@JsonProperty`, or `@JsonTypeInfo` is needed.
* Bad: the `AuditEventType` enum and the `deserialize` switch are a coupled pair; adding a
  new event type requires changes in both places.
* Neutral: `record` requires Java 16+ (JEP 395) and `sealed interface` requires Java 17+
  (JEP 409); this project uses a JDK 25 toolchain.

### Confirmation

* `AuditEventDetails` is declared as `sealed interface` with `permits` listing all record
  subtypes.
* `AuditEventRepository.deserialize()` uses an exhaustive `switch` expression over
  `AuditEventType`; each arm calls `objectMapper.readValue()` with the matching record
  class.
* Jackson is auto-configured by Spring Boot 4.x (`tools.jackson`); no subtype registration
  or `@JsonTypeInfo` annotation is present on `AuditEventDetails`.

## Pros and Cons of the Options

### Sealed Interface with Record Subtypes

* Good: compile-time exhaustiveness checking over `AuditEventType` enum values in the
  `deserialize` switch expression
* Good: each subtype is immutable, structurally comparable, and carries only its own fields
* Good: Jackson 3.x serializes records without annotation; deserialization is handled
  explicitly in the repository, not via Jackson's polymorphism mechanism
* Bad: deserialization is driven by `AuditEventType`, coupling the enum and the switch

### Abstract Class Hierarchy with `@JsonTypeInfo`

* Good: Jackson handles polymorphic deserialization automatically via a `type` field
* Bad: requires a `type` discriminator in the JSON payload, coupling the serialized form to
  the class hierarchy
* Bad: no compile-time enforcement that all subtypes are covered; a new subtype that is
  not registered causes a runtime `InvalidTypeIdException`
* Bad: class hierarchy with abstract base class adds inheritance for what is purely a data
  structure

### Single `Map<String, Object>` or Untyped JSON Node

* Good: no structural coupling between event types and Java types
* Bad: field access is untyped; errors surface at runtime rather than at compile time
* Bad: no self-documentation of which fields belong to which event type

## More Information

* [`AuditEventDetails.java`](../../service/src/main/java/com/github/milez42/featureflags/audit/AuditEventDetails.java)
* [`AuditEventType.java`](../../service/src/main/java/com/github/milez42/featureflags/audit/AuditEventType.java)
* [`AuditEventRepository.java`](../../service/src/main/java/com/github/milez42/featureflags/audit/AuditEventRepository.java)
* [JEP 409 — Sealed Classes](https://openjdk.org/jeps/409)
* [JEP 395 — Records](https://openjdk.org/jeps/395)
