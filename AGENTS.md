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
- **[API / Framework contract]** Convert Java-side domain models, validation result records, and
  persistence entities into Kotlin `*Response` DTOs before generating API responses. OpenAPI
  response schemas should reference the public response DTO, not the internal Java model.
- **[API / Framework contract]** If an Actuator health group references a contributor that can be
  absent in some application contexts, such as `db` when a test excludes `DataSource`
  auto-configuration, disable group membership validation or move that group configuration behind a
  profile so Spring Boot can start consistently.
- **[Testability]** Build test request JSON with `ObjectMapper` when values are supplied through
  variables, so escaping stays correct and helpers do not become fragile string templates.
