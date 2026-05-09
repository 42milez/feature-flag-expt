# Persistence Layer: Design Philosophy and Migration Thinking

This note captures the rationale and evolving thinking behind the persistence layer
choices in this project. It complements [ADR 0002](../decisions/0002-use-spring-data-jdbc-instead-of-jpa.md),
which records the point-in-time decision. Unlike an ADR, this document may be updated
as the project evolves.

## Philosophy: Add Complexity Only When the Problem Demands It

The persistence stack follows a deliberate progression:

```
Spring Data JDBC          ← default; covers CRUD, evaluation reads, audit writes
  │
  ├─ add jOOQ             ← when queries outgrow hand-written SQL strings
  │
  └─ migrate to JPA       ← when object-graph complexity is the real problem
```

The guiding principle is to stay at the lowest level of complexity that the current
access patterns actually require. Each step up the stack introduces implicit behavior
(type-safe DSL generation, dirty checking, lazy loading) that pays off only when the
problem is large enough to justify it.

## When to Add jOOQ (Before Considering JPA)

Prefer jOOQ over JPA when the driver is **query complexity**, not object-graph depth:

- The same JOIN query appears in five or more repository methods with minor variations.
- Read-side queries become complex enough that hand-written SQL strings are hard to
  compose, validate, or refactor safely.
- A CQRS split is introduced: JDBC remains on the command (write) side while the
  query (read) side needs type-safe, optimized SQL.

jOOQ can be added incrementally alongside Spring Data JDBC without replacing it.
Prefer jOOQ over JPA when the driver is complex queries rather than rich object graphs.

## When to Migrate to Spring Data JPA

Consider JPA when the driver is **object-graph complexity**, not query complexity:

- Entity relationships become deep (four or more levels of nesting) and managing the
  object graph manually in repository code becomes repetitive or error-prone.
- Partial updates across large aggregates are frequent. Spring Data JDBC saves an
  aggregate by updating the root entity (all columns) and deleting then reinserting all
  child collections on every save — it has no dirty checking. When this behaviour causes
  concurrency or performance issues, JPA's dirty checking and relationship management
  become worth the added complexity.
- Object-graph traversal becomes central to business logic rather than an edge case.

## When to Stay with Spring Data JDBC at Scale

Spring Data JDBC remains appropriate even as the system grows if:

- Each service in a microservice architecture owns a small, self-contained bounded
  context — the system is large, but no individual service's persistence layer is.
- The architecture uses CQRS: JDBC on the write side, jOOQ or custom SQL on the
  read side.
- The access pattern is performance-critical and read-heavy (e.g., flag evaluation,
  rate limiting, A/B assignment), where explicit SQL and predictable execution are
  more valuable than ORM convenience.

## JPA Complexity as a Service-Extraction Signal

When the signals above appear, they often indicate that the domain itself has grown
complex enough to warrant its own service boundary — not just a persistence upgrade.

Before adopting JPA within the existing codebase, evaluate whether the domain should
instead be extracted into a separate service. Service extraction is the right move when
**at least two of the following** are also true:

- **Change frequency**: the domain changes at a different rate than the rest of the codebase.
- **Team ownership**: a distinct team or role is responsible for this domain.
- **Deploy cycle**: the domain benefits from independent deployment.

Splitting only on persistence complexity risks creating a distributed monolith —
separate deployments that are still tightly coupled in behavior and data. Persistence
complexity alone is a necessary but not sufficient reason to extract.

If the domain does not meet the extraction criteria, migrate to JPA within the existing
service rather than splitting prematurely.

## Relationship to ADR 0002

ADR 0002 records the decision to use Spring Data JDBC and documents the accepted
trade-offs. This note captures the migration signals and service-extraction thinking
that extend beyond the ADR's point-in-time scope.
