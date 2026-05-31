# Review Instructions

Review this repository as a Spring Boot 4 feature-flag service implemented with
Java and Kotlin.

Focus on correctness, security, API behavior, persistence boundaries,
observability, and test coverage. Pay special attention to deterministic
rollout behavior, production policy validation, audit-event atomicity, and
environment-scoped flag evaluation.

Respect these project constraints when suggesting changes:

- Use `tools.jackson.*` imports for Jackson 3.x integration.
- Treat repositories as Spring Data JDBC repositories, not JPA or Hibernate
  repositories.
- Preserve the separation between internal domain or persistence types and
  public API response DTOs.
- Keep generated OpenAPI output consistent with the committed
  `docs/openapi.yaml` snapshot when API behavior changes.
- Add or update tests for behavior changes, especially policy enforcement,
  evaluation outcomes, audit persistence, and error responses.

Prefer narrowly scoped recommendations with concrete file references. Identify
security-sensitive findings first, then behavioral regressions, missing tests,
and maintainability concerns.
