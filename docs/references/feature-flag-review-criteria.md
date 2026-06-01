# Feature Flag Review Criteria

Feature Flag review criteria are split by review phase so reviewers can keep each pass focused:

- [Feature Flag Design Review Criteria](./feature-flag-design-review-criteria.md) for design
  documents, ADRs, plans, requirements, and architecture proposals.
- [Feature Flag Implementation Review Criteria](./feature-flag-implementation-review-criteria.md)
  for Java/Kotlin implementation code, directories, diffs, commits, and pull requests.

Security is intentionally included in both phase-specific references because Feature Flag trust
boundaries, authorization, isolation, audit, fallback, rollout, and context handling are design and
implementation concerns.

Use **Pass**, **Needs justification**, or **Fail** where a formal review calls for an overall
assessment. Treat **Needs justification** as acceptable when the target documents a proportionate
trade-off and the residual risk fits the project's maturity.
