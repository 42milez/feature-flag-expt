---
name: review-feature-flag-design
description: Review Feature Flag design documents, ADRs, plans, and requirements with phase-appropriate security, modern-practice, lifecycle, and over-engineering checks.
---

# Review Feature Flag Design

Use this skill when asked to review a Feature Flag design document, ADR, plan, issue, or
requirements proposal. The review should judge whether the design treats flags as production
control surfaces without turning this project into a feature-management platform before it needs
one.

For deeper criteria, read `docs/references/feature-flag-design-review-criteria.md` only when the
review needs more detail than this skill provides.

## Input

Accept one or more design-oriented targets, such as Markdown documents, ADRs, planning issues, PR
descriptions, or requirements. If a path is provided, resolve it relative to the current working
directory unless it is absolute. If the target is missing or ambiguous, ask for the review target
before proceeding.

If the target mixes design and implementation, review the design decisions in this pass and mention
that implementation code can be reviewed separately with `review-feature-flag-implementation`.

## Workflow

1. Identify the category of each proposed flag: Release, Experiment, Ops, or
   Permissioning/Entitlement.
2. Weight evidence by category:
   - Release: short lifetime, simple decisions, safe defaults, cleanup.
   - Experiment: stable cohorts, measurable interactions, privacy-safe context.
   - Ops: dynamic control, kill-switch reliability, auditability, clear failure behavior.
   - Permissioning/Entitlement: authorization model, tenant isolation, fail-closed behavior,
     durable audit.
3. Review only design-level decisions: trust boundaries, APIs, data model shape, lifecycle,
   governance, operational behavior, and test strategy.
4. Treat **Needs justification** as acceptable when the document explains the trade-off and the
   residual risk fits the project's maturity.
5. Raise only actionable, grounded findings. Avoid checklist dumps unless explicitly requested.

## Must-Check Design Areas

- Flag category, owner, purpose, creation date, and review or removal date are explicit.
- Sensitive flags that gate billing, regulated data, tenant isolation, privileged actions, or kill
  switches receive stricter defaults even when not labeled Permissioning.
- Tenant and environment are explicit isolation boundaries for evaluation, preview, mutation, audit,
  cache keys, and defaults.
- Fail-open or fail-closed behavior is category-specific for missing config, provider failure,
  stale cache, malformed config, and inconsistent versions.
- Preview and validation paths cannot mutate persisted production state or bypass production
  policies.
- Management and mutation flows require server-side authorization, policy checks, and durable audit.
- Runtime configuration is available only where the use case needs it; static config stays acceptable
  for simple short-lived Release flags.
- OpenFeature vocabulary and shapes are compatible where useful without implementing a full SDK
  without a concrete consumer.
- The proposal avoids complex rule DSLs, segmentation, approvals, streaming, SDKs, or provider
  plugins until there is a real consumer.
- Cleanup, stale-flag detection, and follow-up tests are part of delivery.

## Useful Commands

```bash
test -e <path>
find <path> -type f \( -name '*.md' -o -name '*.yaml' -o -name '*.yml' \)
rg -n "feature|flag|rollout|experiment|entitlement|permission|tenant|environment|audit|override|kill|cache|OpenFeature|provider|context|targeting|lifecycle|cleanup" <path>
```

## Output Format

For this repository, write the review response in Japanese unless the user requests another
language. Start with actionable findings ordered by severity. Prefer no more than five primary
findings by default.

For each finding, include the affected design decision or document section, the risk, and the
recommended fix. If there are no actionable findings, say so clearly and call out any meaningful
residual design risk, test gap, or assumption.
