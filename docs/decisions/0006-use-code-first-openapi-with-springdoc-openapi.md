---
status: "accepted"
date: 2026-05-11
decision-makers: ["Akihiro TAKASE"]
consulted: []
informed: []
---

# Use Code-First OpenAPI with springdoc-openapi

## Context and Problem Statement

Phase 4 introduces OpenAPI documentation for the feature flag service. The
project already has Spring MVC controllers, request and response records, Bean
Validation constraints, and Spring `ProblemDetail` error handling.

The goal of this phase is to make the API easier for consumers to discover and
try without redesigning the API surface around a separately-authored OpenAPI
specification.

Which source should define the OpenAPI contract for the current service: the
existing Spring MVC code, a separate spec-first OpenAPI document, or a manually
maintained static snapshot?

## Decision Drivers

* OpenAPI should be introduced without a large rewrite of existing controllers
  and DTOs.
* API documentation should stay close to the Spring MVC request mappings,
  request and response types, and Bean Validation constraints.
* Local API exploration should be available through Swagger UI.
* The project should stay on the Spring Boot 4 and Jackson 3 stack for
  application HTTP JSON handling; OpenAPI support must not require adding a
  Jackson 2 fallback for the service API.
* Phase 4 should document the API shape that exists now, while leaving stricter
  contract governance for a later phase.

## Considered Options

* Code-first OpenAPI with springdoc-openapi (chosen)
* Spec-first OpenAPI with generated server code
* Manually maintained static `docs/openapi.yaml`

## Decision Outcome

Chosen option: **code-first OpenAPI with springdoc-openapi**, because it fits the
current shape of the service and keeps the API documentation close to the code
that actually handles requests.

The Spring MVC controllers, request and response DTOs, Bean Validation
annotations, and focused Swagger annotations are the source of truth for the
OpenAPI document. `springdoc-openapi-starter-webmvc-ui` exposes Swagger UI at
`/swagger-ui.html` and generated OpenAPI documents at `/v3/api-docs` and
`/v3/api-docs.yaml`.

The Phase 4 documented API surface is:

* `POST /api/flags`
* `GET /api/flags/{flagKey}`
* `PATCH /api/flags/{flagKey}`
* `POST /api/evaluate`
* `GET /api/flags/{flagKey}/audit-events`

Error responses continue to use Spring's existing `ProblemDetail` representation.
The project does not add a dedicated error DTO for OpenAPI.

`AuditEventResponse.details` is intentionally published as a loose object schema
in Phase 4. The runtime value is a concrete sealed `AuditEventDetails` record,
but the OpenAPI schema does not model every detail variant with `oneOf` yet.
That keeps the initial documentation small and avoids treating the audit details
wire shape as a fully governed public contract before consumers need that
precision.

No Jackson 2 fallback or direct Jackson 2 dependency is added for service API
serialization. Spring Boot 4 and Jackson 3 remain the application HTTP JSON
baseline, even though some infrastructure libraries can still bring
`com.fasterxml.jackson.*` modules transitively for their own internals.

### Consequences

* Good: OpenAPI documentation stays near the controller methods, DTO fields, and
  validation rules that define the runtime API.
* Good: Swagger UI makes the API easy to inspect locally once the service is
  running.
* Good: Phase 4 adds documentation without introducing generated controller
  code or a spec-first workflow.
* Good: existing `ProblemDetail` error handling remains the error contract.
* Bad: contract governance is weaker than a spec-first workflow; annotations and
  generated output can drift from intended API policy unless checked.
* Bad: Swagger UI and `/v3/api-docs` are enabled by default. Production profiles
  must explicitly set `springdoc.api-docs.enabled=false` and
  `springdoc.swagger-ui.enabled=false` to avoid exposing the API surface.
* Bad: publishing `AuditEventDetails` as a loose object schema means tightening
  it with `oneOf` or variant-specific schemas in a later phase may be a breaking
  change for existing clients.
* Neutral: committed snapshot generation and future schema drift checks are
  covered by ADR-0007; this decision only selects the code-first source of truth
  for the generated OpenAPI contract.

### Confirmation

* `springdoc-openapi-starter-webmvc-ui` is present in the service build.
* `springdoc.swagger-ui.path` is configured as `/swagger-ui.html`.
* `OpenApiConfig` provides the OpenAPI title, version, and description.
* `FeatureFlagController` is annotated with focused `@Tag`, `@Operation`, and
  `@ApiResponse` metadata.
* Request and response DTOs include focused `@Schema` annotations.
* `OpenApiIntegrationTest` verifies Swagger UI, `/v3/api-docs` target paths,
  representative schemas such as `FeatureFlagResponse`,
  `EvaluateFeatureFlagResponse`, `AuditEventResponse`, and `ProblemDetail`, and
  `/v3/api-docs.yaml` availability with representative OpenAPI content.
* Gradle `dependencyInsight` for `tools.jackson.core:jackson-databind` confirms
  the application HTTP JSON stack uses Jackson 3 through Spring Boot 4.
* Gradle `dependencyInsight` for `com.fasterxml.jackson.core:jackson-databind`
  is used to confirm any Jackson 2 modules are transitive infrastructure
  dependencies, not a direct service API fallback.

## Pros and Cons of the Options

### Code-First OpenAPI with springdoc-openapi

* Good: low adoption cost for an existing Spring MVC service
* Good: keeps request mappings, validation, and documentation close together
* Good: exposes Swagger UI and generated JSON/YAML documents automatically
* Bad: the generated contract depends on framework and annotation behavior
* Bad: additional checks are needed if the committed snapshot must never drift

### Spec-First OpenAPI with Generated Server Code

* Good: makes the OpenAPI document the primary API contract
* Good: stronger fit for consumer-first API governance
* Good: generated server interfaces or stubs can catch type-level drift between
  the contract and implementation earlier.
* Bad: higher migration cost for the existing controller-first codebase
* Bad: generated interfaces and models would add another layer that must stay
  aligned with the existing Spring MVC controllers and DTOs

### Manually Maintained Static `docs/openapi.yaml`

* Good: simple to read and commit
* Good: does not depend on runtime OpenAPI generation
* Bad: easy for the static file to drift from controllers and DTOs
* Bad: duplicates API shape already expressed in Spring MVC code

## More Information

* [ADR-0007: Generate the Committed OpenAPI Snapshot with the springdoc Gradle Plugin](0007-generate-committed-openapi-snapshot-with-springdoc-gradle-plugin.md)
* [`service/build.gradle.kts`](../../service/build.gradle.kts)
* [`application.yaml`](../../service/src/main/resources/application.yaml)
* [`OpenApiConfig.java`](../../service/src/main/java/com/github/milez42/featureflags/OpenApiConfig.java)
* [`FeatureFlagController.java`](../../service/src/main/java/com/github/milez42/featureflags/flags/FeatureFlagController.java)
* [`AuditEventResponse.java`](../../service/src/main/java/com/github/milez42/featureflags/audit/AuditEventResponse.java)
* [`OpenApiIntegrationTest.java`](../../service/src/test/java/com/github/milez42/featureflags/OpenApiIntegrationTest.java)
