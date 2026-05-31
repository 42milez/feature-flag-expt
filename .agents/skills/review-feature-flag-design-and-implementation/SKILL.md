---
name: review-feature-flag-design-and-implementation
description: Review Feature Flag design documents, implementation code, diffs, or PRs against modern practice, security attack surface, and appropriate complexity criteria.
---

# Review Feature Flag Design And Implementation

Use this skill when asked to review Feature Flag design or implementation. The review should judge
whether the work follows modern Feature Flag practice, treats flags as production control surfaces,
and avoids building a feature-management platform before the project needs one.

For deeper rationale and the full human-facing checklist, read
`docs/references/feature-flag-review-criteria.md` only when the review needs more detail than this
skill provides.

## Input

Accept one or more review targets:

- design documents, ADRs, plans, or requirements;
- source files or directories;
- diffs, commits, issues, or pull requests;
- a mixed design-and-code review scope.

If the target is missing or ambiguous, ask for the review target before proceeding. If a path is
provided, resolve it relative to the current working directory unless it is absolute.

## Review Method

1. Identify the flag category for each reviewed flag or proposed flag: Release, Experiment, Ops,
   or Permissioning/Entitlement.
2. Use the category to weight evidence:
   - Release: short lifetime, simple decision points, safe defaults, cleanup, and limited rule
     complexity.
   - Experiment: stable cohort assignment, metrics integrity, controlled experiment interaction,
     and privacy-safe context.
   - Ops: fast dynamic control, kill-switch reliability, auditability, low-latency evaluation, and
     clear fail-open/fail-closed behavior.
   - Permissioning/Entitlement: server-side authorization, tenant isolation, fail-closed behavior,
     durable audit, and tests that call protected APIs directly.
3. Read enough surrounding context to understand behavior, data flow, authorization boundaries,
   persistence effects, cache behavior, observability, and tests.
4. Judge each applicable area as **Pass**, **Needs justification**, or **Fail**.
5. Treat **Needs justification** as acceptable only when the design documents the trade-off and the
   residual risk is proportionate to the project's maturity.
6. Raise findings only when they are actionable and grounded in the target. Avoid failing a small
   service only because it does not implement a complete flag platform.

## Core Review Criteria

### Modern Practice Alignment

- Flags have an explicit category, owner, purpose, creation date, and review or removal date.
- Category drives governance: lifetime, default behavior, cleanup policy, authorization, audit, and
  approval requirements differ by category.
- Release flags are short-lived and have expected removal dates.
- Experiment cohorts are stable for the experiment duration unless re-bucketing is intentional and
  documented.
- Concurrent experiments are mutually exclusive or their overlap is intentionally modeled and
  measurable.
- Long-lived Ops and Permissioning flags have stronger ownership, audit, fallback, and authorization
  controls.
- Evaluation APIs return typed values rather than unvalidated strings.
- Every evaluation call has an explicit safe default or documented fail-closed behavior.
- Missing flags, type mismatch, malformed config, timeouts, and provider errors do not crash ordinary
  application paths.
- Evaluation output or telemetry can diagnose flag key, value or variant, reason, and error state
  when applicable.
- Hot-path evaluation avoids blocking network calls, unbounded rule evaluation, and heavy parsing per
  request.
- Server-side dynamic context is the default assumption. Client-side static context must be explicit
  and reviewed for provider status, reconciliation, and event handling.
- Business code evaluates through a service or provider boundary instead of reading persistence
  models directly.
- Evaluation context is a bounded first-class object, not ad hoc parameter sprawl.
- Result metadata can map to OpenFeature-style reason, variant or value, and error details.
- Hooks and events are considered where useful for telemetry, validation, cache refresh,
  `PROVIDER_READY`, `PROVIDER_CONFIGURATION_CHANGED`, `PROVIDER_ERROR`, and `PROVIDER_STALE`.
- OpenFeature compatibility is pragmatic: use compatible vocabulary and data shapes without
  implementing a full SDK unless there is a concrete consumer.
- Decision points in business code stay simple. Targeting, rollout, kill switch, override, and
  fallback logic live in one evaluator or policy layer.
- Long-lived flags avoid scattered branches. Prefer strategy objects, policy objects, or dependency
  injection when a persistent flag affects many modules.
- Flag keys are stable and describe the feature or capability, not rollout percentage, environment,
  or a temporary implementation detail.
- Dynamic configuration is available for canary, kill switch, Ops, Experiment, and Entitlement use
  cases that must change without redeploying.
- Static deploy-time configuration remains acceptable for simple, short-lived Release flags when
  dynamic control would add unnecessary complexity.
- Operators can see current flag state, recent changes, and evaluation behavior.
- Stale flags are detectable through expired review dates, completed rollouts, no recent changes, or
  reference checks.
- Cleanup is part of delivery, including branch removal and obsolete configuration deletion.

### Security And Attack Surface

- Client-side flags are never treated as access control. Server-side authorization still protects
  data and actions.
- Permissioning flags fail closed on missing, stale, or failed evaluations.
- Billing, plan, role, tenant, and compliance decisions come from trusted server-side data.
- Tests call protected APIs directly instead of relying only on hidden UI assertions.
- Environment and tenant are isolation boundaries for evaluation, preview, mutation, audit, cache
  keys, and defaults.
- Cache keys include flag key, environment, tenant or project scope, and provider version when
  relevant.
- Preview and validation APIs cannot mutate persisted production state or bypass production
  policies.
- Evaluation context fields have explicit limits for field count, key length, value length,
  collection size, and nesting depth.
- Context values are acyclic JSON/YAML-like structures, not arbitrary object graphs.
- Only supported targeting attributes are accepted or used.
- PII is minimized, hashed, or redacted where possible.
- User-supplied context cannot choose unauthorized environments, tenants, variants, policies, or
  override modes.
- Logs and metrics do not emit sensitive context or high-cardinality values as labels.
- Cookies, headers, query parameters, and debug endpoints are disabled unless documented.
- Overrides are authenticated, scoped by environment/tenant/user, temporary, and audited.
- Test or preview overrides cannot silently affect public production traffic.
- Create, update, delete, rollout, kill-switch, approval, and policy operations have
  environment-aware authorization checks.
- Risky production rollout, allowlist removal, kill-switch disablement, and permissioning changes
  require validation or approval.
- Audit events include actor, timestamp, correlation ID, flag key, environment, old value, new value,
  and reason when available.
- Audit records are durable, append-only when possible, and preserved after flag deletion.
- Fail-open/fail-closed behavior is category-specific and documented for missing config, provider
  failure, stale cache, partial reads, malformed config, and inconsistent versions.
- Stale provider or cache state is detectable and visible to operators.
- Kill-switch evaluation is simple, fast, and harder to break than ordinary targeting rules.
- Percentage rollout uses an OpenFeature targeting key or equivalent stable, non-secret subject
  identifier instead of attacker-controlled request fields.
- Rollout is deterministic for the same context unless configuration changes.
- Rule count, nesting, collection sizes, and evaluation cost are bounded.
- Targeting rules do not execute arbitrary scripts or unsafe expressions.

### Over-Engineering Control

- The system solves current use cases before adding platform features.
- Segmentation, experiments, approvals, SDKs, streaming, and provider plugins have real consumers
  before they are built.
- Short-lived Release flags do not require complex rule DSLs, approval flows, or segmentation by
  default.
- New abstractions are justified because they reduce real duplication, enable testing, or support a
  known integration.
- OpenFeature vocabulary is used consistently where useful, but full provider contract behavior is
  deferred until a consumer needs it.
- Internal APIs remain readable and preserve this service's domain language.
- Current data shapes can migrate toward OpenFeature concepts without a large rewrite.
- Rules are understandable, bounded, explicit, and represented as structured policy objects rather
  than arbitrary scripts or string expressions.
- Rule precedence is documented for kill switch, allowlist, percentage rollout, targeting rules, and
  defaults.
- Evaluation telemetry shows useful counts, decisions, reasons, errors, and fallback usage without
  noisy high-cardinality labels.
- Mutation telemetry makes updates, kill-switch changes, approval decisions, and policy rejections
  easy to find.
- High-volume successful evaluation logs are sampled or scoped when needed.
- Decision logic has focused unit tests for status, kill switch, environment, allowlist, percentage
  rollout, defaults, and failure behavior.
- Security boundaries have integration tests for tenant/environment isolation, mutation
  authorization, preview isolation, and audit persistence.
- Risky policies have regression tests for production rollout increases, allowlist removal, and
  kill-switch changes.
- Provider boundaries are testable with an in-memory provider, fake provider, or evaluator test
  double.
- Tests target important interactions and precedence rules instead of exhaustively testing every flag
  combination.

## OpenFeature Reference Points

Use OpenFeature as an interoperability target and design language, not as a mandatory platform
implementation. Helpful concepts include:

- typed evaluation with explicit defaults and error behavior;
- provider boundaries between application code and flag storage;
- evaluation context with a stable targeting key;
- resolution details with reason, variant or value, and error information;
- hooks and provider events for readiness, configuration change, error, and stale state.

## Useful Commands

```bash
test -e <path>
find <path> -type f \( -name '*.java' -o -name '*.kt' -o -name '*.md' \)
rg -n "feature|flag|rollout|experiment|entitlement|permission|tenant|environment|audit|override|kill|cache|OpenFeature|provider|context|targeting" <path>
```

## Output Format

For this repository, write the review response in Japanese unless the user requests another
language. Start with actionable findings ordered by severity. For each finding, include the affected
file and line when reviewing code, the affected behavior or design decision, the risk, and the
recommended fix.

Use this compact structure when a formal review report is requested:

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

If there are no actionable findings, say so clearly and call out any meaningful residual design risk,
test gap, or assumption.
