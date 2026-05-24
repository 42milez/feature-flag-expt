---
name: review-feature-flag-impl
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

- Treat Feature Flag management endpoints as privileged control-plane surfaces. Create, update,
  delete, kill-switch, rollout percentage, target environment, tenant allowlist, approval, and
  audit-event operations must be protected by server-side authentication and authorization; do not
  rely on caller-supplied booleans, headers, or client-side checks as the trust boundary.
- Require rate limits, deployment access controls, and flag-existence non-disclosure behavior for
  management APIs exposed beyond a trusted network.
- Enforce safety policies on the server-side write path, not only through preview or validation
  endpoints, so alternate clients cannot bypass rollout constraints.

### Sensitive Flags And Defaults

- Classify flags as sensitive when they gate billing, personal or regulated data, tenant isolation,
  privileged actions, operational kill switches, or other high-impact behavior. Apply stricter
  sensitive-flag rules by default when impact is unclear.
- Use safe creation defaults for sensitive flags. New sensitive flags should start disabled or at
  zero rollout, and creating a flag with immediate broad exposure should go through the same
  server-side policy and approval checks as later rollout expansion.
- Make fail-open Feature Flag behavior explicit and rare. For sensitive or privileged features,
  missing flag state at startup or evaluation time, failed evaluation, unavailable storage,
  timeouts, stale cache entries, or malformed targeting context should default to disabled,
  least-privilege behavior, or refusing startup when safe defaults cannot be guaranteed.

### Evaluation Trust Boundary

- Do not trust client-supplied Feature Flag evaluation results, rollout buckets, or targeting
  attributes for authorization, billing, data access, tenant isolation, operational kill switches,
  or other privileged behavior.
- Derive sensitive evaluation context from authenticated server-side identity whenever the result
  gates privileged behavior.
- Keep evaluation context identifiers opaque and non-PII. Use stable internal IDs instead of
  emails, names, phone numbers, or raw personal data, and avoid logging or returning targeting
  identifiers unless the caller is authorized and the data is necessary for diagnostics.

### Response Detail And Enumeration

- Minimize evaluation response detail for sensitive flags. Public or client-facing evaluation APIs
  should not expose rollout buckets, rule-match reasons, allowlist matches, or other targeting
  internals unless the caller is authorized for diagnostic detail.
- Protect flag keys and flag metadata from enumeration. Management, get, list, evaluate, preview,
  validation, and audit endpoints should require authorization, apply rate limits where exposed,
  and avoid response shapes or error behavior that let unauthorized callers map the flag namespace.

### Kill Switches And Caching

- Design kill-switch changes to propagate faster than ordinary rollout changes. Any cache, CDN,
  SDK, or local evaluation layer that can retain flag state must define a kill-switch invalidation
  path, such as short emergency TTLs, active cache purge, event-driven invalidation, or bypassing
  cached enabled results.

### Rollout Bucketing

- Do not make sensitive rollout membership predictable from caller-chosen identifiers. When
  deterministic bucketing protects privileged or high-impact exposure, prevent arbitrary probing by
  deriving identifiers server-side, rate limiting evaluation, or using a long-lived server-side
  bucket salt.
- Store bucket salts in a secrets manager, KMS-backed configuration store, or equivalent managed
  secret storage; do not commit them to source code, bake them into images, or place them in
  plaintext configuration or environment variables that are not sourced from protected secret
  storage.
- Treat salt rotation as a planned migration because it can change bucket assignments for every
  evaluated subject.

### Audit Surface

- Audit Feature Flag changes that alter user or tenant exposure, including creation, deletion,
  status, rollout percentage, kill-switch state, target environments, tenant allowlists, approvals,
  and policy overrides.
- Persist audit records atomically with the write they describe, derive the actor or service
  identity from authenticated server-side identity such as the session principal, JWT subject,
  OAuth client ID, or service account, and avoid leaking sensitive targeting data in logs or
  responses.
- Separate audit-log read authorization from flag write authorization. Audit endpoints can expose
  tenant identifiers, targeting history, approval context, and actor identities, so reads must be
  scoped by role, tenant, environment, and ownership before returning audit details.
- Preserve audit-log retention and integrity for all flag changes covered by the audit rule. Audit
  records must remain available after a flag is deleted and must not be removed through flag-key
  cascade deletes.
- Prefer append-only or tamper-evident audit storage, restrict direct mutation of audit records,
  and forward those flag changes to external logging or SIEM systems when available.

### Tenant And Environment Boundaries

- Preserve tenant and environment boundaries in Feature Flag management, preview, validation, and
  evaluation APIs.
- Do not allow flag keys, allowlists, sample contexts, or evaluation inputs to cross tenant or
  environment scope without server-side authorization and an explicit key-space or ownership model.

## Output Format

- Start with actionable findings, ordered by severity.
- For each finding, include the file and line, the affected behavior, the risk, and the requested
  fix.
- Keep summaries brief and secondary to findings.
- If there are no findings, state that clearly and call out any meaningful residual risk or missing
  test coverage.
