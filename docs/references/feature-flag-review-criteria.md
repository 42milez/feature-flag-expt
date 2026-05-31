# Feature Flag Review Criteria

This checklist evaluates Feature Flag design and implementation against three
goals:

- follow modern Feature Flag practices as of 2026;
- treat Feature Flags as production control surfaces with real attack surface;
- avoid building a feature-management platform before the project needs one.

Use these criteria during design review, code review, and security review. Each
item should be judged as **Pass**, **Needs justification**, or **Fail**. A
"Needs justification" result is acceptable when the implementation documents the
trade-off and the risk is proportionate to the project stage.

## Applying The Checklist By Flag Category

Not every item has the same weight for every flag. Use the flag category to
decide how much evidence is needed before accepting "Needs justification."

| Category | Strongest review focus |
|---|---|
| Release | Short lifetime, simple decision points, safe defaults, cleanup, and limited rule complexity. |
| Experiment | Stable cohort assignment, metrics integrity, mutually exclusive or intentionally overlapping experiments, and privacy-safe context. |
| Ops | Fast dynamic control, kill-switch reliability, auditability, low-latency evaluation, and clear fail-open/fail-closed behavior. |
| Permissioning/Entitlement | Server-side authorization, tenant isolation, fail-closed behavior, durable audit, and tests that call protected APIs directly. |

## References

- [OpenFeature](https://openfeature.dev/) for vendor-neutral Feature Flag API
  concepts.
- [OpenFeature introduction](https://openfeature.dev/docs/reference/intro/) for
  SDK, provider, hook, and event concepts.
- [OpenFeature flag evaluation](https://openfeature.dev/specification/sections/flag-evaluation/)
  for typed evaluation, defaults, error behavior, and result metadata.
- [OpenFeature evaluation context](https://openfeature.dev/docs/reference/concepts/evaluation-context/)
  for targeting context and privacy considerations.
- [OpenFeature SDK paradigms](https://openfeature.dev/docs/reference/concepts/sdk-paradigms/)
  for dynamic server-side context and static client-side context differences.
- [OpenFeature events](https://openfeature.dev/specification/sections/events/)
  for provider readiness, configuration-change, error, and stale-state events.
- [Feature Toggles](https://martinfowler.com/articles/feature-toggles.html) for
  toggle categories, lifetime, decision isolation, and operational trade-offs.

## 1. Modern Practice Alignment

### 1.1 Flag Taxonomy

| Criteria | Pass signal |
|---|---|
| Flags are categorized by purpose. | Each flag is classified as Release, Experiment, Ops, or Permissioning/Entitlement. |
| Category drives governance. | Lifetime, owner, default behavior, cleanup policy, and required approval differ by category. |
| Release flags stay short-lived. | Release flags have expected removal dates and are not treated as permanent configuration. |
| Experiment cohorts are stable. | A subject receives consistent treatment for the experiment duration unless explicitly re-bucketed. |
| Experiment interactions are controlled. | Concurrent experiments are mutually exclusive or their overlap is intentionally modeled and measurable. |
| Long-lived flags receive stronger controls. | Ops and Permissioning flags have explicit ownership, audit, fallback, and authorization requirements. |

Review notes:

- A flag without a category is harder to reason about than a flag with a simple
  implementation.
- Permissioning flags should be reviewed as authorization-adjacent behavior, not
  as ordinary rollout switches.

### 1.2 Evaluation API Shape

| Criteria | Pass signal |
|---|---|
| Evaluation is typed. | APIs return typed values such as boolean, string, number, or structured object rather than unvalidated strings. |
| Defaults are explicit. | Every evaluation call has a safe default or a documented fail-closed behavior. |
| Provider failure is contained. | Missing flags, type mismatch, malformed config, timeout, and provider errors do not crash ordinary application paths. |
| Results are diagnosable. | Evaluation output or telemetry includes flag key, value or variant, reason, and error state when applicable. |
| Evaluation can be called on hot paths. | The design avoids blocking network calls, unbounded rule evaluation, or heavy parsing per request. |
| SDK paradigm is explicit. | Server-side dynamic context is the default assumption; client-side static context is documented when in scope. |

Review notes:

- Error behavior should be category-aware. A permissioning flag should fail
  closed; a non-critical UI release flag may safely fall back to disabled.
- A boolean-only API is acceptable for an early service if variants and typed
  details are intentionally out of scope.
- This checklist assumes server-side, dynamic-context evaluation unless a
  client-side SDK or browser/mobile evaluation path is explicitly in scope. If
  client-side static context is in scope, review context reconciliation,
  provider status, and client-side event handling as part of the API design.

### 1.3 OpenFeature Alignment

| Criteria | Pass signal |
|---|---|
| Application code is separated from storage. | Business code evaluates through a service/provider-like boundary instead of reading persistence models directly. |
| Evaluation context is first class. | Targeting inputs are passed as a bounded context object rather than ad hoc method parameters. |
| Result metadata is compatible in spirit. | Reason, variant/value, and error information can be mapped to OpenFeature-style resolution details if needed. |
| Extension points are intentional. | Hooks/events are considered for telemetry, validation, cache refresh, `PROVIDER_READY`, `PROVIDER_CONFIGURATION_CHANGED`, `PROVIDER_ERROR`, and `PROVIDER_STALE`. |
| Compatibility is pragmatic. | The service avoids incompatible vocabulary and data shapes, but does not implement a full OpenFeature SDK without a concrete consumer. |

Review notes:

- OpenFeature alignment is a design guardrail, not a requirement to become a
  complete flag platform.
- Provider boundaries are valuable once there is a real second provider, SDK
  consumer, test double, or external integration.
- If a provider can serve cached or remote configuration, stale-state events
  should feed the failure-mode review in [2.6 Failure Modes](#26-failure-modes).

### 1.4 Decision Isolation

| Criteria | Pass signal |
|---|---|
| Decision points stay simple. | Business code asks clear questions such as "is checkout-redesign enabled?" rather than assembling targeting logic. |
| Decision logic is centralized. | Targeting, rollout, kill switch, override, and fallback logic live in one evaluator or policy layer. |
| Long-lived flags avoid branch scatter. | Persistent differences use strategy objects, policy objects, or dependency injection instead of many repeated `if` branches. |
| Flag naming is stable and descriptive. | Keys describe the feature or capability, not a temporary implementation detail. |

Review notes:

- Short-lived release flags can be simpler than long-lived permissioning or ops
  flags.
- A flag that affects many modules should have a named decision abstraction.
- Flag key format is a project convention. Prefer stable feature or capability
  names; do not encode environment, rollout percentage, or temporary
  implementation details in the key unless the project has explicitly chosen
  that convention.

### 1.5 Runtime Configuration

| Criteria | Pass signal |
|---|---|
| Dynamic configuration is used where needed. | Canary, kill switch, ops, experiment, and entitlement use cases can change without redeploying. |
| Static configuration remains allowed for simple cases. | Short-lived release flags may use deploy-time configuration when dynamic control would add unnecessary complexity. |
| Config changes are visible. | Operators can see current flag state, recent changes, and evaluation behavior. |
| Config changes are auditable. | Mutations record actor, timestamp, target flag, environment, old state, new state, and reason where available. |

Review notes:

- "Prefer static configuration" is too broad for modern production systems.
  The right answer depends on flag category and blast radius.
- Runtime configuration requires stronger controls because it can change
  production behavior outside the deploy pipeline.
- This section asks whether operators can understand what changed. Section
  [2.5 Mutation Governance](#25-mutation-governance) asks whether those changes
  are authorized, complete enough for incident review, and durable enough for
  security governance.

### 1.6 Lifecycle And Cleanup

| Criteria | Pass signal |
|---|---|
| Every flag has lifecycle metadata. | Owner, purpose, category, creation date, and review or removal date are recorded. |
| Stale flags are detectable. | There is a way to find flags with no recent changes, completed rollouts, or expired review dates. |
| Cleanup is part of delivery. | Release work includes removing old decision branches and deleting obsolete configuration. |
| Cleanup risk is understood. | Deleting a flag has tests or code-reference checks for the affected paths. |

Review notes:

- Expiration dates are useful, but they are not enough by themselves. The system
  also needs a practical way to find references and remove dead branches.

## 2. Security And Attack Surface

### 2.1 Authorization And Entitlements

| Criteria | Pass signal |
|---|---|
| Client-side flags are not access control. | UI flags may hide features, but server-side authorization still protects data and actions. |
| Permissioning flags fail closed. | Missing, stale, or failed permissioning evaluations deny access by default. |
| Entitlement sources are authoritative. | Billing, plan, role, tenant, and compliance decisions come from trusted server-side data. |
| Privileged behavior is tested directly. | Tests call protected APIs directly instead of relying only on hidden UI assertions. |

Review notes:

- A hidden button is a UX choice, not a security boundary.
- Treat permissioning flags as part of the authorization model even when they
  share infrastructure with rollout flags.

### 2.2 Tenant And Environment Boundaries

| Criteria | Pass signal |
|---|---|
| Environment is an isolation boundary. | A staging change cannot alter production evaluation through shared config, cache keys, or ambiguous defaults. |
| Tenant is an isolation boundary. | Evaluation, preview, mutation, and audit APIs cannot cross tenant or project scopes. |
| Cache keys include the right scope. | Flag key, environment, tenant/project scope, and provider version are included when relevant. |
| Preview paths are isolated. | Preview or validation APIs cannot mutate persisted production state or bypass production policies. |

Review notes:

- Environment-specific rollout configuration is usually safer than a single
  shared flag configuration with target environment lists.
- Cache bugs can become authorization bugs when tenant or environment is omitted
  from the key.

### 2.3 Evaluation Context Safety

| Criteria | Pass signal |
|---|---|
| Context fields are bounded. | Field count, key length, value length, collection size, and nesting depth have explicit limits. |
| Context structures are serializable. | Structured context values are acyclic, JSON/YAML-like data rather than object graphs with circular references. |
| Context schema is intentional. | Only supported targeting attributes are accepted or used. |
| PII is minimized. | User identifiers, email addresses, IP addresses, and raw device identifiers are avoided, hashed, or redacted where possible. |
| Context cannot select policy. | User-supplied context cannot choose unauthorized environments, tenants, variants, or override modes. |
| Logs and metrics are safe. | High-cardinality or sensitive context fields are not emitted as metric labels or unredacted logs. |

Review notes:

- Evaluation context is externally influenced data in many systems. Validate it
  like request input, not like trusted config.
- OpenFeature defines an optional `targeting key` on evaluation context. If the
  implementation has an equivalent field, treat it as the canonical subject key
  for rollout and targeting rather than deriving identity from arbitrary custom
  fields.

### 2.4 Overrides And Debug Paths

| Criteria | Pass signal |
|---|---|
| Overrides are explicitly designed. | Cookies, headers, query parameters, and debug endpoints are disabled unless there is a documented need. |
| Overrides are authenticated. | Only authorized users or services can activate override behavior. |
| Overrides are scoped and temporary. | Overrides have environment, tenant, user, and expiration constraints. |
| Overrides are audited. | Override creation, use, and removal are observable during incident review. |
| Production behavior is protected. | Test or preview overrides cannot silently affect public production traffic. |

Review notes:

- Per-request override mechanisms are convenient but dangerous. They should be
  treated as privileged operational tools.

### 2.5 Mutation Governance

| Criteria | Pass signal |
|---|---|
| Mutations require authorization. | Create, update, delete, rollout, kill switch, and approval operations have environment-aware permission checks. |
| Risky changes are policy-checked. | Broad production rollout, allowlist removal, kill-switch disablement, and permissioning changes require validation or approval. |
| Audit records are complete. | Audit events include actor, timestamp, correlation ID, flag key, environment, old value, new value, and reason when available. |
| Audit records are durable. | Operational audit events are append-only or otherwise protected from accidental update-in-place behavior. |

Review notes:

- A Feature Flag admin API is a production behavior API. Review it with the same
  seriousness as deployment, access-control, or incident-response tooling.

### 2.6 Failure Modes

| Criteria | Pass signal |
|---|---|
| Fail-open/fail-closed is category-specific. | Each flag category has documented behavior for missing config and provider failure. |
| Stale config is visible. | The system can detect or report stale cache/provider state. |
| Partial reads are handled. | Incomplete config, missing child collections, and inconsistent versions have deterministic behavior. |
| Kill switches remain reliable. | Kill-switch evaluation is simple, fast, and harder to break than ordinary targeting rules. |

Review notes:

- The safest fallback is not always "disabled." It depends on whether the flag
  protects access, reduces operational load, or gates an unfinished feature.
- If the implementation uses an OpenFeature-style provider, `PROVIDER_STALE` is
  a natural mechanism for surfacing stale state. See also
  [1.3 OpenFeature Alignment](#13-openfeature-alignment).

### 2.7 Rollout And Targeting Abuse Resistance

| Criteria | Pass signal |
|---|---|
| Rollout keys are stable. | Percentage rollout uses OpenFeature `targeting key` or an equivalent stable, non-secret subject identifier rather than attacker-controlled request fields. |
| Rollout is deterministic. | The same context receives the same result unless config changes. |
| Rule complexity is bounded. | Rule count, nesting, collection sizes, and evaluation cost have limits. |
| Rule language is safe. | Targeting does not execute arbitrary scripts or unsafe expressions. |

Review notes:

- Deterministic rollout is both an operational feature and a security property:
  users should not be able to manipulate request attributes to hunt for variants.
- If no stable subject key exists, percentage rollout should be rejected or
  explicitly documented as approximate and unsuitable for experiments.

## 3. Over-Engineering Control

### 3.1 Complexity Matches Maturity

| Criteria | Pass signal |
|---|---|
| The system solves current use cases. | Core evaluation, mutation, audit, and lifecycle needs are clear before adding platform features. |
| Advanced features have consumers. | Segmentation, experiments, approvals, SDKs, streaming, and provider plugins are added only when there is a real use case. |
| Simple flags stay simple. | Short-lived release flags do not require complex rule DSLs, approval flows, or segmentation by default. |
| Platform boundaries are justified. | New abstractions are introduced because they reduce real duplication, enable testing, or support a known integration. |

Review notes:

- Avoid building a commercial feature-management product by accident.
- The best first abstraction is often a clear evaluator service, not a generic
  rule engine.

### 3.2 Pragmatic OpenFeature Adoption

| Criteria | Pass signal |
|---|---|
| Vocabulary is compatible. | Concepts such as provider, evaluation context, reason, variant, hook, and event are used consistently. |
| Full SDK behavior is deferred until needed. | The service does not implement the entire OpenFeature provider contract unless external consumers require it. |
| Internal APIs remain readable. | OpenFeature alignment does not obscure domain names used by this service. |
| Migration path is preserved. | Current shapes can be adapted to OpenFeature concepts without a large rewrite. |

Review notes:

- OpenFeature is most useful here as an interoperability target and design
  language unless the project explicitly exposes an SDK/provider.

### 3.3 Rule Design

| Criteria | Pass signal |
|---|---|
| Rules are understandable. | A reviewer can explain why a context receives a value without simulating many overlapping systems. |
| Rules are bounded. | The model limits rule count, nesting, and supported operators. |
| Rules are explicit. | Prefer structured policy objects over arbitrary scripts or string expressions. |
| Rule precedence is documented. | Kill switch, allowlist, percentage rollout, targeting rules, and defaults have clear order. |

Review notes:

- A rule system becomes a programming language quickly. Add operators slowly and
  test their interactions.

### 3.4 Observability Without Noise

| Criteria | Pass signal |
|---|---|
| Evaluation telemetry is useful. | Metrics or traces show evaluation count, enabled/disabled decisions, reason, errors, and fallback usage. |
| Labels stay low-cardinality. | Raw user IDs, request IDs, tenant IDs at high scale, and exception messages are excluded from metric labels. |
| Mutation telemetry is prominent. | Updates, kill-switch changes, approval decisions, and policy rejections are easy to find. |
| Logs are sampled or scoped when needed. | High-volume successful evaluations do not create excessive log cost. |

Review notes:

- Observability should answer "why did this request get this result?" and "what
  changed before the incident?" without leaking sensitive context.

### 3.5 Testing Strategy

| Criteria | Pass signal |
|---|---|
| Decision logic has focused unit tests. | Status, kill switch, environment, allowlist, percentage rollout, defaults, and failure behavior are covered. |
| Security boundaries have integration tests. | Tenant/environment isolation, mutation authorization, preview isolation, and audit persistence are tested. |
| Risky policies have regression tests. | Production rollout increases, allowlist removal, and kill-switch changes are covered. |
| Provider boundaries are testable. | Unit tests can use an in-memory provider, fake provider, or evaluator test double without requiring a real flag backend. |
| Combinatorics are controlled. | Tests target known interactions instead of exhaustively testing every possible flag combination. |

Review notes:

- Feature Flag systems often fail through interaction bugs. Test the order of
  precedence and the boundaries that carry production risk.

## Review Output Template

Use this compact template when recording findings:

```markdown
## Feature Flag Review

### Overall Result

- Modern practice alignment: Pass | Needs justification | Fail
- Security posture: Pass | Needs justification | Fail
- Over-engineering control: Pass | Needs justification | Fail

### Findings

| Severity | Area | Finding | Recommendation |
|---|---|---|---|
| High | Security | ... | ... |
| Medium | Modern practice | ... | ... |
| Low | Over-engineering | ... | ... |

### Notable Justifications

- ...

### Follow-up Tests

- ...
```
