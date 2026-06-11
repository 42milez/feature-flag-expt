---
name: review-feature-flag-implementation
description: Review Feature Flag implementation code, directories, diffs, commits, or pull requests with phase-appropriate security, correctness, testability, and complexity checks.
---

# Review Feature Flag Implementation

Use this skill when asked to review Feature Flag implementation code, a directory, diff, commit, or
pull request. The review should focus on behavior in the target while reading enough surrounding
context to judge data flow, authorization boundaries, persistence effects, cache behavior,
observability, and tests.

For deeper criteria, read `docs/references/feature-flag-implementation-review-criteria.md` only
when the review needs more detail than this skill provides.

## Input

Accept Java, Kotlin, configuration, test, diff, commit, or PR targets. If a path is provided,
resolve it relative to the current working directory unless it is absolute. If a directory is
provided, recursively include Java and Kotlin files and any directly relevant tests or
configuration.

If the target is mainly a design document, use `review-feature-flag-design` instead. If a target
mixes design and code, review the implementation behavior in this pass and mention any design-only
questions separately.

## Workflow

1. Establish the reviewed target set before judging behavior.
2. Read target files and enough context outside the target to understand evaluation, mutation,
   authorization, persistence, caching, observability, and tests.
3. Review only behavior grounded in the target set. Use outside files as context, not as extra
   review scope.
4. Apply security checks as part of implementation review, not as a separate checklist pass.
5. Raise only actionable findings with precise file and line references. Avoid checklist dumps
   unless explicitly requested.
6. If no actionable issues are found, say so clearly and mention residual test or design risk.

## Must-Check Implementation Areas

- Business code evaluates through an evaluator, service, or provider boundary instead of reading
  persistence models directly.
- Evaluation APIs use typed values, explicit defaults, deterministic rule precedence, and
  category-appropriate failure behavior.
- Caller-supplied context cannot select unauthorized environments, tenants, variants, policies,
  override modes, rollout buckets, or privileged behavior.
- Evaluation context fields have explicit bounds for field count, key length, value length,
  collection size, and nesting depth.
- Management operations enforce server-side authentication, authorization, rollout policy, and
  safety checks on the write path.
- Audit records derive actors from authenticated server-side identity, persist atomically with flag
  writes, and survive flag deletion.
- Cache keys include flag key, environment, tenant or project scope, and provider version when those
  dimensions affect evaluation.
- Overrides, debug paths, preview paths, and validation APIs cannot silently affect public
  production traffic or bypass production policies.
- Percentage rollout uses stable server-trusted subject identifiers; sensitive bucketing does not
  rely on caller-chosen identifiers or committed plaintext salts.
- Logs, metrics, and responses do not expose sensitive context, high-cardinality labels, flag
  namespaces, rollout internals, or rule-match details to unauthorized callers.
- Tests cover important precedence, failure, tenant/environment isolation, authorization, audit, and
  policy-boundary behavior without exhaustive flag-combination testing.
- Implementation complexity fits current use cases; rule engines, provider plugins, SDK behavior,
  approvals, and streaming are introduced only for concrete consumers.

## Useful Commands

```bash
test -e <path>
find <path> -type f \( -name '*.java' -o -name '*.kt' -o -name '*.yaml' \)
rg -n "feature|flag|rollout|tenant|environment|audit|authorize|permission|cache|salt|override|debug|context|bucket|script|policy|preview|validation|OpenFeature|provider" <path>
```

## Output Format

For this repository, write the review response in Japanese unless the user requests another
language. Start with actionable findings ordered by severity. Prefer no more than five primary
findings by default.

For each finding, include the affected file and line, the risky behavior, and the requested fix. If
there are no findings, state that clearly and call out any meaningful residual risk or missing test
coverage.
