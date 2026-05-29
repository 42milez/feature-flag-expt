---
name: review-feature-flag-security
description: Review Java and Kotlin files under a supplied file or directory path for Feature Flag attack-surface risks, with findings grounded in the implementation and surrounding context.
---

# Review Feature Flag Implementation

Use this skill when asked to review Feature Flag implementation code from a file or directory path.
The review is security-focused and should treat Feature Flag systems as high-impact control
surfaces because they can change user, tenant, environment, billing, operational, or privileged
behavior at runtime.

## Input

Accept exactly one file or directory path. If the path is missing, ambiguous, or does not exist, ask
for a valid path before reviewing.

## Workflow

1. Resolve the supplied path relative to the current working directory unless it is absolute.
2. If the path is a file, include it only when it is a Java or Kotlin file: `.java` or `.kt`.
3. If the path is a directory, recursively list Java and Kotlin files under it and use that list as
   the review target set.
4. Read the target files and enough surrounding code outside the target path to understand behavior,
   data flow, authorization boundaries, persistence effects, cache behavior, and tests.
5. Review only behavior in the target files. Use code outside the target set only as context for
   judging that behavior.
6. Produce code-review findings first, ordered by severity, with precise file and line references.
   Include why the behavior is risky and what concrete change would address it.
7. If no actionable issues are found, say so clearly and mention any residual test or design risk.

## Useful Commands

```bash
test -e <path>
find <path> -type f \( -name '*.java' -o -name '*.kt' \)
rg -n "feature|flag|rollout|tenant|environment|audit|authorize|permission|cache|salt" <path>
```

## Attack-Surface Checklist

Use this checklist to evaluate Java and Kotlin code in the target set. A finding should be tied to
behavior in the target files, not raised only because an idealized implementation could be more
complete.

### Control Plane

- #must-check Management operations such as create, update, delete, kill-switch, rollout,
  environment targeting, tenant allowlists, approvals, and audit events must enforce server-side
  authentication and authorization.
- #evidence Do not accept caller-supplied booleans, headers, or client-side checks as the trust
  boundary for privileged flag changes.
- #context-dependent For management APIs exposed beyond a trusted network, require rate limits,
  deployment access controls, and flag-existence non-disclosure.
- #fix Enforce rollout and safety policies on the server-side write path, not only in preview or
  validation endpoints, so alternate clients cannot bypass them.

### Sensitive Flags And Defaults

- #must-check Classify flags as sensitive when they gate billing, personal or regulated data,
  tenant isolation, privileged actions, kill switches, or other high-impact behavior; apply stricter
  rules by default when impact is unclear.
- #must-check Sensitive flags should start disabled or at zero rollout. Immediate broad exposure
  should require the same server-side policy and approval checks as later rollout expansion.
- #must-check Sensitive or privileged flags should fail closed: disabled, least privilege, or
  startup refusal when safe defaults cannot be guaranteed.
- #evidence Look for fail-open paths on missing state, evaluation errors, storage outages,
  timeouts, stale cache entries, or malformed targeting context.

### Evaluation Trust Boundary

- #must-check Never use client-supplied evaluation results, rollout buckets, or targeting
  attributes to authorize billing, data access, tenant isolation, kill switches, or privileged
  behavior.
- #fix Derive sensitive evaluation context from authenticated server-side identity whenever a flag
  gates privileged behavior.
- #evidence Keep context identifiers opaque and non-PII. Prefer stable internal IDs over emails,
  names, phone numbers, or raw personal data, and avoid logging or returning targeting identifiers
  unless the caller is authorized and the data is needed for diagnostics.

### Response Detail And Enumeration

- #must-check Protect flag keys and metadata from enumeration across management, get, list,
  evaluate, preview, validation, and audit endpoints.
- #must-check Minimize sensitive evaluation responses. Do not expose rollout buckets, rule-match
  reasons, allowlist matches, or targeting internals unless the caller is authorized for diagnostic
  detail.
- #context-dependent For exposed APIs, require authorization, rate limits, and non-disclosing
  errors or response shapes that prevent unauthorized namespace mapping.

### Kill Switches And Caching

- #must-check Kill-switch changes should propagate faster than ordinary rollout changes.
- #evidence Any cache, CDN, SDK, or local evaluation layer that retains flag state needs an
  emergency invalidation path, such as short TTLs, active purge, event-driven invalidation, or
  bypassing cached enabled results.

### Rollout Bucketing

- #must-check Sensitive rollout membership must not be predictable from caller-chosen identifiers.
- #fix For deterministic bucketing that protects privileged or high-impact exposure, derive
  identifiers server-side, rate limit evaluation, or use a long-lived server-side bucket salt.
- #must-check Store bucket salts in a secrets manager, KMS-backed configuration store, or
  equivalent managed secret storage; do not commit them, bake them into images, or place them in
  plaintext configuration or unprotected environment variables.
- #context-dependent Treat salt rotation as a planned migration because it can change assignments
  for every evaluated subject.

### Audit Surface

- #must-check Audit flag changes that alter user or tenant exposure, including creation, deletion,
  status, rollout percentage, kill-switch state, target environments, tenant allowlists, approvals,
  and policy overrides.
- #must-check Persist audit records atomically with the flag write and derive actor identity from
  authenticated server-side identity.
- #evidence Check session principals, JWT subjects, OAuth client IDs, or service accounts; avoid
  logging or returning sensitive targeting data.
- #must-check Scope audit-log reads separately from flag writes by role, tenant, environment, and
  ownership because audit details can expose tenant IDs, targeting history, approvals, and actors.
- #must-check Preserve audit retention and integrity after flag deletion; audit records must not be
  removed by flag-key cascade deletes.
- #context-dependent Prefer append-only or tamper-evident audit storage, restrict direct mutation,
  and forward flag changes to external logging or SIEM systems when available.

### Tenant And Environment Boundaries

- #must-check Preserve tenant and environment boundaries in Feature Flag management, preview,
  validation, and evaluation APIs.
- #evidence Flag keys, allowlists, sample contexts, and evaluation inputs must not cross tenant or
  environment scope without server-side authorization and an explicit key-space or ownership model.

## Output Format

- Write the response in Japanese.
- Start with actionable findings, ordered by severity.
- For each finding, include the file and line, the affected behavior, the risk, and the requested
  fix.
- Keep summaries brief and secondary to findings.
- If there are no findings, state that clearly and call out any meaningful residual risk or missing
  test coverage.
