---
status: "accepted"
date: 2026-05-10
decision-makers: ["Akihiro TAKASE"]
consulted: []
informed: []
---

# Enforce Audit Atomicity with `Propagation.MANDATORY`

## Context and Problem Statement

`AuditEventService.record()` writes an audit event row whenever a feature flag is
mutated. The audit event must land in the same database transaction as the flag
mutation itself. If the two writes commit independently, either of the following
failure modes becomes possible:

* The flag mutation commits but `record()` fails → a flag change with no audit trail.
* `record()` commits but the flag mutation is rolled back → a phantom audit event
  with no corresponding state change.

Either outcome leaves the audit log inconsistent with the committed flag state.
The transactional propagation setting on `AuditEventService.record()` is the key
control point for ensuring that audit writes invoked from mutation flows participate
in the caller's transaction.

## Decision Drivers

* Audit events must be atomically consistent with flag mutations: both succeed or
  both roll back together.
* `AuditEventService` is an internal collaborator, not a standalone service; it has
  no currently supported use case outside a caller that already holds a transaction.
* Misconfigured callers should fail visibly at call time instead of creating audit
  rows in a transaction boundary that is separate from the flag mutation.

## Considered Options

* `Propagation.MANDATORY` (chosen)
* `Propagation.REQUIRED` (Spring default)
* `Propagation.REQUIRES_NEW`

## Decision Outcome

Chosen option: **`Propagation.MANDATORY`**, because it enforces at the framework level
that `record()` can only be called from within an active transaction. Any caller
without a transaction receives an `IllegalTransactionStateException` immediately,
making the misuse visible at call time — if the code path is exercised in tests,
the bug surfaces in CI; if not, the exception still fires when the misconfigured
call path is executed rather than creating an independently committed audit row.

This relies on the normal Spring declarative transaction model: `record()` must be
invoked through Spring's transactional proxy. The current mutation paths satisfy
that condition because `FeatureFlagService` calls `AuditEventService` as a separate
Spring bean from within `@Transactional` service methods.

### Consequences

* Good: for mutation paths that call `record()` inside their transaction, the flag
  mutation and its audit event commit atomically. Those paths cannot commit one
  write without the other.
* Good: calling `record()` without an active transaction throws
  `IllegalTransactionStateException` immediately, surfacing misconfigured callers
  at call time rather than allowing an independently committed audit write.
* Trade-off: `AuditEventService` cannot be called from a non-transactional context (e.g.,
  a scheduled job or event listener that has not opened a transaction). Any future
  caller must ensure a transaction is active before invoking `record()`.

## Pros and Cons of the Options

### `Propagation.MANDATORY`

* Good: the atomicity contract is enforced — `record()` always participates in the
  caller's transaction, when invoked through Spring's transactional proxy, and
  cannot silently write outside it
* Good: misconfigured callers fail fast with a clear exception
* Bad: imposes a constraint on every future caller; any new call site must be inside
  a transaction

### `Propagation.REQUIRED` (Spring default)

* Good: zero friction for callers — works with or without an existing transaction
* Good: participates in the caller's transaction when one already exists, so the
  current `FeatureFlagService` call paths would remain atomic
* Bad: without an active transaction, Spring silently opens a standalone
  transaction for `record()` alone, allowing a future caller to decouple audit
  writes from flag mutations without a framework error

### `Propagation.REQUIRES_NEW`

* Good: always runs in its own transaction regardless of the caller
* Bad: if the outer transaction rolls back after the inner transaction commits,
  the audit record remains durable — a phantom event with no corresponding state change
* Bad: the committed audit row is visible to other readers before the flag mutation
  completes, creating a window of inconsistency

## More Information

* [`AuditEventService.java`](../../service/src/main/java/com/github/milez42/featureflags/audit/AuditEventService.java)
* [`FeatureFlagService.java`](../../service/src/main/java/com/github/milez42/featureflags/flags/FeatureFlagService.java)
* [Spring Framework — Transaction Propagation](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/tx-propagation.html)
