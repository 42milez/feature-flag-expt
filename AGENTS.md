# Project Instructions

- All written content — source code comments, documentation (README.md, etc.), and any other text artifacts — must be in English.
- After completing any implementation task, run the following checks in order and confirm all pass before reporting the task as done. **Skip these checks if the only files changed are under `docs/` (e.g., ADRs in `docs/decisions/`).**
  1. `./gradlew :service:spotlessCheck` — formatting
  2. `./gradlew :service:compileJava` — static analysis (Error Prone)
  3. `./gradlew :service:test` — full test suite

## Technology Notes

- This project uses Spring Boot 4.x, which auto-configures Jackson 3.x (`tools.jackson`).
  Always use `tools.jackson.*` imports, never `com.fasterxml.jackson.*` (Jackson 2.x).
  Mixing the two causes `NoSuchBeanDefinitionException` at runtime.

- This project uses Spring Data JDBC (`spring-boot-starter-data-jdbc`), not Spring Data JPA.
  JPA / Hibernate is not on the classpath. `CrudRepository` methods (e.g., `save`, `deleteAll`)
  are implemented by Spring Data JDBC and execute SQL via `JdbcTemplate` — not via an ORM session.
  Do not describe any repository behavior as "JPA-managed" or "Hibernate-backed".

## Coding Rules

When implementing or refactoring code in this project, add a new rule below **only** if the work reveals a principle that satisfies at least one of the following criteria and is non-obvious (i.e., not already enforced by Spotless, Error Prone, or evident from the surrounding code):

- **Security**: The change prevents or mitigates a concrete vulnerability class (e.g., input validation gap, timing attack, improper credential handling, injection risk, insecure default).
- **Modern Java**: The change replaces a pre-Java-21 idiom with a canonical modern equivalent that meaningfully improves clarity or safety (e.g., adopting `switch` pattern matching, replacing a class hierarchy with a `sealed interface` + `record` ADT, replacing `Optional.get()` with `orElseThrow`).
- **Modern Kotlin**: The change replaces a Java-oriented or pre-idiomatic Kotlin pattern with a canonical Kotlin equivalent that meaningfully improves clarity, null-safety, or type-safety (e.g., using `data class`, `sealed interface`, extension functions, scope functions, or nullable types instead of manual boilerplate or sentinel values).
- **API / Framework contract**: The change fixes or prevents a silent violation of a library or framework contract that could cause subtle runtime failures (e.g., correct use of Spring Data `Persistable`, `@Transactional` propagation semantics, Jackson deserialization contracts).
- **Observability**: The change adds or corrects structured logging, metrics, or tracing in a way that materially improves diagnosability in production.
- **Testability**: The change restructures code to eliminate a category of test fragility (e.g., replacing time-dependent logic with an injected `Clock`, isolating I/O behind an interface).

Do **not** add a rule for:
- Pure cosmetic renaming or whitespace changes.
- Changes that simply conform to style already enforced by Spotless or Error Prone.
- Incremental improvements with no generalizable principle.

Format for each entry (add under the relevant category heading, creating the heading if it does not exist):

```
- **[Category]** _What to do / avoid_, and why. Reference the relevant class or pattern if helpful.
```

- **[Security]** Put explicit size limits on externally supplied collections before iterating over
  them in request handlers or services. Bean Validation constraints such as `@Size(max = ...)`
  should document and enforce the bound at the API edge.
- **[Security]** Enforce safety policies on the server-side write path, not only through preview
  or validation endpoints, so alternate clients cannot bypass rollout constraints.
- **[Security]** Treat feature flag management endpoints as privileged control-plane
  surfaces. Create, update, delete, kill-switch, rollout percentage, target environment,
  tenant allowlist, approval, and audit-event operations must be protected by
  server-side authentication and authorization; do not rely on caller-supplied booleans,
  headers, or client-side checks as the trust boundary. Add rate limits, deployment
  access controls, and flag-existence non-disclosure behavior for management APIs when
  they are exposed beyond a trusted network.
- **[Security]** Classify flags as sensitive when they gate billing, personal or
  regulated data, tenant isolation, privileged actions, operational kill switches, or
  other high-impact behavior. Apply the stricter sensitive-flag rules by default when
  the impact is unclear.
- **[Security]** Do not trust client-supplied feature flag evaluation results, rollout
  buckets, or targeting attributes for authorization, billing, data access, tenant
  isolation, operational kill switches, or other privileged behavior. Derive sensitive
  evaluation context from authenticated server-side identity whenever the result gates
  privileged behavior.
- **[Security]** Minimize evaluation response detail for sensitive flags. Public or
  client-facing evaluation APIs should not expose rollout buckets, rule-match reasons,
  allowlist matches, or other targeting internals unless the caller is authorized for
  diagnostic detail.
- **[Security]** Protect flag keys and flag metadata from enumeration. Management, get,
  list, evaluate, preview, validation, and audit endpoints should require authorization,
  apply rate limits where exposed, and avoid response shapes or error behavior that
  let unauthorized callers map the flag namespace.
- **[Security]** Make fail-open feature flag behavior explicit and rare. For sensitive
  or privileged features, missing flag state at startup or evaluation time, failed
  evaluation, unavailable storage, timeouts, stale cache entries, or malformed targeting
  context should default to disabled, least-privilege behavior, or refusing startup when
  safe defaults cannot be guaranteed.
- **[Security]** Use safe creation defaults for sensitive flags. New sensitive flags
  should start disabled or at zero rollout, and creating a flag with immediate broad
  exposure should go through the same server-side policy and approval checks as later
  rollout expansion.
- **[Security]** Design kill-switch changes to propagate faster than ordinary rollout
  changes. Any cache, CDN, SDK, or local evaluation layer that can retain flag state
  must define a kill-switch invalidation path, such as short emergency TTLs, active
  cache purge, event-driven invalidation, or bypassing cached enabled results.
- **[Security]** Do not make sensitive rollout membership predictable from caller-chosen
  identifiers. When deterministic bucketing protects privileged or high-impact exposure,
  prevent arbitrary probing by deriving identifiers server-side, rate limiting evaluation,
  or using a long-lived server-side bucket salt. Store bucket salts in a secrets manager,
  KMS-backed configuration store, or equivalent managed secret storage; do not commit
  them to source code, bake them into images, or place them in plaintext configuration
  or environment variables that are not sourced from protected secret storage. Treat salt
  rotation as a planned migration because it can change bucket assignments for every
  evaluated subject.
- **[Security]** Keep evaluation context identifiers opaque and non-PII. Use stable
  internal IDs instead of emails, names, phone numbers, or raw personal data, and avoid
  logging or returning targeting identifiers unless the caller is authorized and the data
  is necessary for diagnostics.
- **[Security]** Audit feature flag changes that alter user or tenant exposure, including
  creation, deletion, status, rollout percentage, kill-switch state, target environments,
  tenant allowlists, approvals, and policy overrides. Persist audit records atomically
  with the write they describe, derive the actor or service identity from authenticated
  server-side identity such as the session principal, JWT subject, OAuth client ID, or
  service account, and avoid leaking sensitive targeting data in logs or responses.
- **[Security]** Separate audit-log read authorization from flag write authorization.
  Audit endpoints can expose tenant identifiers, targeting history, approval context,
  and actor identities, so reads must be scoped by role, tenant, environment, and
  ownership before returning audit details.
- **[Security]** Preserve audit-log retention and integrity for all flag changes covered
  by the audit rule. Audit records must remain available after a flag is deleted and
  must not be removed through flag-key cascade deletes. Prefer append-only or
  tamper-evident audit storage, restrict direct mutation of audit records, and forward
  those flag changes to external logging or SIEM systems when available.
- **[Security]** Preserve tenant and environment boundaries in feature flag management,
  preview, validation, and evaluation APIs. Do not allow flag keys, allowlists, sample
  contexts, or evaluation inputs to cross tenant or environment scope without server-side
  authorization and an explicit key-space or ownership model.
- **[API / Framework contract]** Convert Java-side domain models, validation result records, and
  persistence entities into Kotlin `*Response` DTOs before generating API responses. OpenAPI
  response schemas should reference the public response DTO, not the internal Java model.
- **[API / Framework contract]** If an Actuator health group references a contributor that can be
  absent in some application contexts, such as `db` when a test excludes `DataSource`
  auto-configuration, disable group membership validation or move that group configuration behind a
  profile so Spring Boot can start consistently.
- **[Testability]** Build test request JSON with `ObjectMapper` when values are supplied through
  variables, so escaping stays correct and helpers do not become fragile string templates.
