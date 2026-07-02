# Project Instructions

- All written content — source code comments, documentation (README.md, etc.), and any other text artifacts — must be in English unless it is a Japanese localization file such as `README.ja.md` or `docs/development.ja.md`.
- When updating one file in an English/Japanese documentation pair, update the counterpart in the
  same change so both versions stay in sync. Current pairs are `README.md` / `README.ja.md` and
  `docs/development.md` / `docs/development.ja.md`.
- When updating `README.ja.md`, use natural Japanese phrasing rather than a literal translation
  of the English text.
- In `README.ja.md`, keep each Japanese prose paragraph on a single physical line so Markdown
  renderers do not insert unwanted half-width spaces at source line breaks; rely on editor soft
  wrap for readability.
- When updating `docs/development.ja.md`, use natural Japanese phrasing rather than a literal
  translation of the English text.
- After completing any implementation task, run the following checks in order and confirm all pass before reporting the task as done **only when code under `service/` has changed**. Skip these checks when no files under `service/` were changed, such as documentation-only updates under `docs/`, local agent skill changes under `.agents/`, or edits to `AGENTS.md` itself.
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
- **[Security]** When adding Spring Security route rules, finish with an authenticated fallback
  such as `anyRequest().authenticated()` so newly added routes do not become public unless they are
  explicitly listed as public.
- **[Security]** Add an explicit deny-all rule for protected API namespaces before the final
  authenticated fallback, so new API routes fail closed until they are deliberately classified.
- **[Security]** Explicitly configure the access level of exposed non-health Actuator endpoints,
  such as `management.endpoint.prometheus.access=read-only`, so their permitted operations remain
  intentional even if framework defaults change.
- **[Security]** Do not treat stateless HTTP Basic authentication as inherently CSRF-safe, because
  browsers can automatically resend Basic credentials. Keep CSRF protection or replace Basic
  authentication before supporting browser-authenticated API clients.
- **[Security]** Derive audit actor identity from trusted server-side authentication context, not
  request payloads, so callers cannot forge who performed a state-changing operation.
- **[Security]** Derive rollout risk and approval satisfaction from trusted server-side state, not
  request payloads, so callers cannot self-authorize protected rollout changes.
- **[Security]** When a vulnerability gate reports fixed high/critical findings in runtime
  dependencies or image packages, update the dependency or base image before adding ignore entries,
  so the gate remains a real protection rather than a noise filter.
- **[Security]** When excluding unfixed vulnerability findings from a blocking CI gate, publish a
  non-blocking report that still includes them, so reviewers can see relevant risk without stopping
  unrelated changes.
- **[Security]** Do not apply vulnerability severity filters to secret scanning jobs, because a
  leaked credential remains actionable even when the scanner assigns it a lower severity.
- **[API / Framework contract]** Convert Java-side domain models, validation result records, and
  persistence entities into Kotlin `*Response` DTOs before generating API responses. OpenAPI
  response schemas should reference the public response DTO, not the internal Java model.
- **[API / Framework contract]** If an Actuator health group references a contributor that can be
  absent in some application contexts, such as `db` when a test excludes `DataSource`
  auto-configuration, disable group membership validation or move that group configuration behind a
  profile so Spring Boot can start consistently.
- **[API / Framework contract]** When enabling Spring Boot graceful shutdown, set
  `server.shutdown=graceful` in addition to the shutdown phase timeout, and keep that timeout below
  the platform termination grace period so the server actually drains requests before the pod exits.
- **[Observability]** Pin provisioned Grafana datasource UIDs when dashboards reference datasource
  variables as UIDs, so dashboard imports and pod restarts do not break panels by generating a new
  datasource identifier.
- **[Testability]** Build test request JSON with `ObjectMapper` when values are supplied through
  variables, so escaping stays correct and helpers do not become fragile string templates.
