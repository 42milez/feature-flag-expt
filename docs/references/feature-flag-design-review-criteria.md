# Feature Flag Design Review Criteria

Use this reference when a Feature Flag design review needs deeper criteria than
`review-feature-flag-design` provides. The goal is to judge design decisions before implementation
details hide or harden risky boundaries.

Each applicable area may be judged as **Pass**, **Needs justification**, or **Fail**. Accept
**Needs justification** when the design documents a proportionate trade-off and the residual risk
fits the project's maturity.

## Flag Category Weighting

| Category | Strongest review focus |
|---|---|
| Release | Short lifetime, simple decision points, safe defaults, cleanup, and limited rule complexity. |
| Experiment | Stable cohort assignment, metrics integrity, controlled interaction with concurrent experiments, and privacy-safe context. |
| Ops | Fast dynamic control, kill-switch reliability, auditability, low-latency evaluation, and clear fail-open/fail-closed behavior. |
| Permissioning/Entitlement | Server-side authorization, tenant isolation, fail-closed behavior, durable audit, and direct protected-API tests. |

## Design-Level Criteria

### Taxonomy And Lifecycle

| Criteria | Pass signal |
|---|---|
| Flags are categorized by purpose. | Each flag is classified as Release, Experiment, Ops, or Permissioning/Entitlement. |
| Category drives governance. | Lifetime, owner, default behavior, cleanup policy, authorization, audit, and approval requirements differ by category. |
| Lifecycle metadata exists. | Owner, purpose, category, creation date, and review or removal date are recorded. |
| Stale flags are detectable. | There is a practical way to find flags with expired review dates, completed rollouts, no recent changes, or stale code references. |
| Cleanup is part of delivery. | Release work includes removing obsolete branches and deleting unused configuration. |

### Evaluation API And Provider Shape

| Criteria | Pass signal |
|---|---|
| Evaluation is typed. | Public and internal evaluation APIs return typed values instead of unvalidated strings. |
| Defaults are explicit. | Every evaluation path defines a safe default or documented fail-closed behavior. |
| Provider failure is contained. | Missing flags, type mismatch, malformed config, timeout, and provider errors have deterministic behavior. |
| Result metadata is diagnosable. | Evaluation result or telemetry can explain value or variant, reason, and error state where useful. |
| Application code is separated from storage. | Business logic evaluates through a service or provider-like boundary. |
| Evaluation context is first class. | Targeting inputs use a bounded context object rather than ad hoc parameter sprawl. |

### Design Security

| Criteria | Pass signal |
|---|---|
| Client-side flags are not access control. | UI flags may hide features, but server-side authorization still protects data and actions. |
| Sensitive flags are restricted by default. | Flags gating billing, regulated data, tenant isolation, privileged actions, or kill switches start disabled or at zero rollout unless policy allows broader exposure. |
| Tenant and environment boundaries are explicit. | Evaluation, preview, mutation, audit, cache keys, and defaults cannot cross tenant or environment scope without authorization. |
| Preview paths are isolated. | Preview or validation APIs cannot mutate persisted production state or bypass production write-path policies. |
| Mutation governance is designed. | Create, update, delete, rollout, kill-switch, and approval flows have environment-aware authorization and server-side policy checks. |
| Audit is durable enough for incidents. | Audit records include actor, timestamp, correlation ID, flag key, environment, old value, new value, and reason where available. |
| Audit identity is trusted. | Actor identity comes from authenticated server-side identity, not caller-supplied fields. |
| Audit retention survives deletion. | Deleting a flag does not erase operational audit history. |

### Failure Modes And Operations

| Criteria | Pass signal |
|---|---|
| Failure behavior is category-specific. | Missing config, provider failure, stale cache, malformed config, partial reads, and inconsistent versions have documented behavior per flag category. |
| Stale state is visible. | Operators can detect or report stale provider or cache state. |
| Kill switches are reliable. | Kill-switch evaluation is simple, fast, and harder to break than ordinary targeting. |
| Emergency invalidation exists where needed. | Cache, CDN, SDK, or local evaluator layers have short TTLs, active purge, event-driven invalidation, or a bypass for cached enabled results. |
| Config changes are observable. | Operators can see current flag state, recent changes, and evaluation behavior. |

### Experiments, Rollout, And Targeting

| Criteria | Pass signal |
|---|---|
| Experiment cohorts are stable. | A subject receives consistent treatment unless re-bucketing is intentional and documented. |
| Experiment overlap is controlled. | Concurrent experiments are mutually exclusive or their overlap is intentionally modeled and measurable. |
| Rollout uses stable subjects. | Percentage rollout uses an OpenFeature targeting key or equivalent stable, non-secret subject identifier. |
| Sensitive membership resists probing. | High-impact rollout membership cannot be predicted from caller-chosen identifiers. |
| Rule complexity is bounded. | The design limits rule count, nesting, collection sizes, operators, and evaluation cost. |
| Rule language is safe. | Targeting rules are structured policy objects, not arbitrary scripts or unsafe expressions. |

### OpenFeature And Complexity

| Criteria | Pass signal |
|---|---|
| OpenFeature alignment is pragmatic. | Vocabulary and data shapes can map to provider, evaluation context, reason, variant, hooks, and events where useful. |
| Full SDK behavior is deferred until needed. | The service does not implement the entire provider contract without a concrete consumer. |
| Decision points stay simple. | Targeting, rollout, kill switch, override, and fallback logic live in one evaluator or policy layer. |
| Advanced platform features have consumers. | Segmentation, experiments, approvals, SDKs, streaming, and provider plugins are added only for real use cases. |
| Observability avoids leakage. | Telemetry can diagnose decisions and changes without exposing sensitive context or high-cardinality labels. |

## Useful References

- [OpenFeature](https://openfeature.dev/) for vendor-neutral Feature Flag API concepts.
- [OpenFeature flag evaluation](https://openfeature.dev/specification/sections/flag-evaluation/)
  for typed evaluation, defaults, error behavior, and result metadata.
- [OpenFeature evaluation context](https://openfeature.dev/docs/reference/concepts/evaluation-context/)
  for targeting context and privacy considerations.
- [Feature Toggles](https://martinfowler.com/articles/feature-toggles.html) for toggle categories,
  lifetime, decision isolation, and operational trade-offs.
