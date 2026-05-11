---
status: "accepted"
date: 2026-05-11
decision-makers: ["Akihiro TAKASE"]
consulted: []
informed: []
---

# Generate the Committed OpenAPI Snapshot with the springdoc Gradle Plugin

## Context and Problem Statement

Code-first OpenAPI with springdoc-openapi is introduced as described in ADR-0006. The generated OpenAPI document is
available from the running service at `/v3/api-docs.yaml`, but the repository
also includes `docs/openapi.yaml` so that the API snapshot is visible without
starting the application.

If `docs/openapi.yaml` is edited by hand, it can easily drift from the
controllers, DTO annotations, and generated runtime OpenAPI document.

How should the project keep the committed OpenAPI snapshot up to date?

## Decision Drivers

* `docs/openapi.yaml` should be reproducible from the runtime OpenAPI endpoint.
* Updating the snapshot should require a single Gradle task, not manual YAML
  editing.
* OpenAPI generation should not require a local PostgreSQL or Docker dependency.
* The generation setup should avoid changing normal application runtime
  behavior.
* CI drift checking is useful, but this ADR should first establish a stable local
  generation workflow.

## Considered Options

* springdoc Gradle plugin with an `openapi-gen` H2 profile (chosen)
* Manual snapshot updates
* Testcontainers PostgreSQL during OpenAPI generation
* Dedicated OpenAPI-only Spring Boot application

## Decision Outcome

Chosen option: **generate `docs/openapi.yaml` with the springdoc OpenAPI Gradle
plugin**, using a dedicated `openapi-gen` Spring profile for the forked
application process.

The `:service:generateOpenApiDocs` task starts the Spring Boot application,
fetches `http://localhost:8080/v3/api-docs.yaml`, and writes the result to
`docs/openapi.yaml`. The committed YAML is therefore a snapshot of the same
runtime OpenAPI document exposed by the service.

The `openapi-gen` profile uses an H2 in-memory database and disables Flyway. This
keeps OpenAPI generation lightweight and avoids requiring PostgreSQL for a task
whose output is derived from controllers, DTOs, Bean Validation, and springdoc
annotations rather than database contents.

H2 is added as a `developmentOnly` dependency because it is only needed for the
forked local generation process. The normal runtime profile continues to use
PostgreSQL and Flyway.

The `forkedSpringBootRun` and `forkedSpringBootStop` tasks are explicitly marked
as incompatible with the Gradle configuration cache because of a
springdoc-openapi-gradle-plugin limitation.

CI drift checking is deferred because it complements, rather than replaces, the
snapshot generation mechanism. A future workflow can run
`:service:generateOpenApiDocs` and then check `git diff --exit-code
docs/openapi.yaml`, but this ADR only establishes the reproducible generation
path.

### Consequences

* Good: `docs/openapi.yaml` can be regenerated with one Gradle task.
* Good: the committed snapshot matches the runtime `/v3/api-docs.yaml` output.
* Good: OpenAPI generation does not require Docker or a local PostgreSQL
  instance.
* Good: the generation-only H2 database is isolated behind the `openapi-gen`
  profile.
* Bad: generating the snapshot starts the application, so it is heavier than a
  pure static export.
* Bad: the generation profile is separate from normal runtime configuration and
  must be kept minimal.
* Bad: generation uses a fixed localhost port through the configured OpenAPI
  endpoint, so the task can fail if port `8080` is already in use.
* Neutral: CI can later enforce that generated output and the committed snapshot
  stay in sync.

### Confirmation

* `org.springdoc.openapi-gradle-plugin` is present in the service build.
* The `openApi` Gradle extension writes `openapi.yaml` under the repository's
  `docs` directory.
* The `openApi` Gradle extension fetches
  `http://localhost:8080/v3/api-docs.yaml`.
* `application-openapi-gen.yaml` configures an H2 in-memory datasource and
  disables Flyway.
* H2 is declared as a `developmentOnly` dependency.
* `README.md` documents Swagger UI and raw OpenAPI endpoints for local use.

## Pros and Cons of the Options

### springdoc Gradle Plugin with an `openapi-gen` H2 Profile

* Good: generates the committed snapshot from the same endpoint used at runtime
* Good: avoids manual YAML edits
* Good: avoids requiring PostgreSQL or Docker for OpenAPI generation
* Bad: requires a dedicated generation profile
* Bad: depends on the springdoc Gradle plugin's forked Spring Boot process

### Manual Snapshot Updates

* Good: no build plugin or generation profile required
* Good: easy for small one-off edits
* Bad: high risk of drift from the generated runtime OpenAPI document
* Bad: reviewers must manually verify that YAML edits match controller and DTO
  changes

### Testcontainers PostgreSQL during OpenAPI Generation

* Good: closer to normal runtime infrastructure
* Good: would exercise startup with PostgreSQL and Flyway
* Bad: too heavy for an API documentation generation task
* Bad: requires Docker even though OpenAPI generation does not depend on database
  data

### Dedicated OpenAPI-Only Spring Boot Application

* Good: could minimize the context needed for OpenAPI generation
* Good: avoids database configuration for generation
* Bad: adds another application entry point and configuration surface
* Bad: can drift from the real application context that exposes `/v3/api-docs.yaml`

## More Information

* [ADR-0006: Use Code-First OpenAPI with springdoc-openapi](0006-use-code-first-openapi-with-springdoc-openapi.md)
* Production profiles must disable springdoc API docs and Swagger UI; see the
  consequences in ADR-0006.
* springdoc-openapi-gradle-plugin issue
  [#166](https://github.com/springdoc/springdoc-openapi-gradle-plugin/issues/166)
  tracks the Gradle configuration cache incompatibility.
* [`service/build.gradle.kts`](../../service/build.gradle.kts)
* [`application-openapi-gen.yaml`](../../service/src/main/resources/application-openapi-gen.yaml)
* [`docs/openapi.yaml`](../openapi.yaml)
* [`README.md`](../../README.md)
