# Feature Flag Implementation Review Criteria

Use this reference when a Feature Flag implementation review needs deeper criteria than
`review-feature-flag-implementation` provides. The goal is to find actionable implementation
defects without turning every review into a platform-completeness audit.

Findings should be tied to behavior in the reviewed target. Use surrounding code only as context
unless the user explicitly includes it in scope.

## Implementation-Level Criteria

### Evaluation Boundary And API Shape

| Criteria | Pass signal |
|---|---|
| Business code uses an evaluation boundary. | Feature decisions go through an evaluator, service, or provider instead of persistence entities. |
| Evaluation is typed. | Boolean, string, numeric, or structured evaluations validate type and default behavior. |
| Defaults are explicit. | Evaluation callers pass or resolve a safe default; sensitive decisions fail closed. |
| Failure behavior is deterministic. | Missing flags, type mismatch, malformed config, timeout, stale cache, and provider errors cannot crash ordinary paths unexpectedly. |
| Result metadata is useful. | Reason, variant or value, and error state are available for diagnostics where needed. |
| Hot paths stay cheap. | Evaluation avoids blocking network calls, heavy parsing, unbounded rule traversal, or repeated expensive deserialization per request. |

### Implementation Security

| Criteria | Pass signal |
|---|---|
| Management operations authorize server-side. | Create, update, delete, rollout, kill-switch, environment targeting, tenant allowlists, approvals, and audit reads enforce authentication and authorization. |
| Write paths enforce policy. | Rollout and safety rules run on production mutation paths, not only in preview or validation endpoints. |
| Caller claims are not trusted. | Caller-supplied booleans, headers, client-side checks, evaluation results, rollout buckets, or targeting attributes do not authorize privileged behavior. |
| Sensitive context is server-derived. | Billing, plan, role, tenant, compliance, and privileged-action decisions use authenticated server-side data. |
| Response detail is scoped. | Flag keys, metadata, rollout buckets, rule-match reasons, allowlist matches, and targeting internals are returned only to authorized diagnostic callers. |
| Errors avoid enumeration. | Exposed APIs do not let unauthorized callers map flag namespaces through response shape, status, timing, or error detail. |

### Tenant, Environment, And Context Safety

| Criteria | Pass signal |
|---|---|
| Tenant and environment scope is preserved. | Evaluation, preview, validation, mutation, audit, cache, and defaults cannot cross scope accidentally. |
| Cache keys include scope. | Flag key, environment, tenant or project scope, and provider version are included when they affect evaluation. |
| Context fields are bounded. | Field count, key length, value length, collection size, and nesting depth have explicit limits. |
| Context structures are safe. | Context values are acyclic JSON/YAML-like data, not arbitrary object graphs that trigger unsafe traversal or serialization. |
| Only supported attributes are used. | User-supplied context cannot choose unauthorized environments, tenants, variants, policies, or override modes. |
| PII and cardinality are controlled. | Logs and metrics avoid sensitive context and high-cardinality labels. |

### Overrides, Preview, And Validation

| Criteria | Pass signal |
|---|---|
| Override mechanisms are explicit. | Cookies, headers, query parameters, and debug endpoints are disabled unless designed as privileged override paths. |
| Overrides are controlled. | Overrides are authenticated, scoped by environment, tenant, and user, temporary, and audited. |
| Preview is isolated. | Test, debug, preview, and validation paths cannot silently affect public production traffic. |
| Validation cannot bypass production policy. | A successful preview or validation result is not treated as permission to skip write-path checks. |

### Audit, Persistence, And Deletion

| Criteria | Pass signal |
|---|---|
| Audit writes are complete. | Audit events capture actor, timestamp, correlation ID, flag key, environment, old value, new value, and reason where available. |
| Actor identity is trusted. | Audit actors come from session principals, JWT subjects, OAuth client IDs, or service accounts rather than request body fields. |
| Audit writes are atomic. | Audit records persist in the same transaction boundary as the flag mutation they describe. |
| Audit reads are separately scoped. | Reading audit logs has role, tenant, environment, and ownership checks separate from flag mutation permissions. |
| Audit survives flag deletion. | Cascade deletes or key reuse do not erase or corrupt operational history. |

### Rollout, Bucketing, And Rule Evaluation

| Criteria | Pass signal |
|---|---|
| Rollout keys are stable. | Percentage rollout uses a stable, non-secret subject key rather than attacker-controlled request fields. |
| Sensitive membership resists probing. | High-impact deterministic rollout uses server-derived identifiers, rate limits, or a protected server-side bucket salt. |
| Bucket salts are protected. | Salts are stored in managed secret storage, not committed, baked into images, or kept in plaintext configuration. |
| Salt rotation is intentional. | Rotation is treated as an assignment-changing migration. |
| Rule evaluation is bounded. | Rule count, nesting, collection sizes, supported operators, and evaluation cost have limits. |
| Rule language is safe. | Targeting rules do not execute arbitrary scripts or unsafe expressions. |
| Precedence is tested. | Status, kill switch, environment, allowlist, percentage rollout, targeting rules, defaults, and failure behavior have focused tests. |

### Observability, Tests, And Complexity

| Criteria | Pass signal |
|---|---|
| Evaluation telemetry is useful. | Metrics or traces show counts, decisions, reasons, errors, and fallback usage without sensitive labels. |
| Mutation telemetry is prominent. | Updates, kill-switch changes, approval decisions, and policy rejections are easy to find. |
| Security boundaries are tested. | Tenant/environment isolation, mutation authorization, preview isolation, and audit persistence have integration tests. |
| Provider boundaries are testable. | Unit tests can use an in-memory provider, fake provider, or evaluator test double. |
| Test combinatorics are controlled. | Tests target important interactions rather than every possible flag combination. |
| Complexity matches current consumers. | Rule engines, provider plugins, SDK behavior, approvals, streaming, and segmentation are introduced only when used. |

## Useful References

- [OpenFeature flag evaluation](https://openfeature.dev/specification/sections/flag-evaluation/)
  for typed evaluation, defaults, error behavior, and result metadata.
- [OpenFeature evaluation context](https://openfeature.dev/docs/reference/concepts/evaluation-context/)
  for targeting context and privacy considerations.
- [OpenFeature events](https://openfeature.dev/specification/sections/events/) for readiness,
  configuration-change, error, and stale-state events.
- [Feature Toggles](https://martinfowler.com/articles/feature-toggles.html) for implementation
  patterns and operational trade-offs.
