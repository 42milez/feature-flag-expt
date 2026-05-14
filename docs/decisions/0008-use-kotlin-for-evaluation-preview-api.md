---
status: "accepted"
date: 2026-05-14
decision-makers: ["Akihiro TAKASE"]
consulted: []
informed: []
---

# Use Kotlin for the Evaluation Preview API

## Context and Problem Statement

The service supports persisted feature flag creation, updates, evaluation, and
audit history. Operators also need a way to inspect the effect of a proposed
change before saving it, especially for rollout, kill switch, tenant allowlist,
and environment-targeting changes.

The preview behavior should compare current and proposed evaluation outcomes for
caller-supplied sample contexts. It must reuse the production evaluator so the
preview semantics stay aligned with normal evaluation, but it must not persist
the proposed change or write audit events.

Which implementation boundary should the project use for this evaluation preview
feature?

## Decision Drivers

* Preview must be read-only and must not create audit events.
* Preview should reuse the existing production `FeatureFlagEvaluator` rather
  than duplicate evaluation rules.
* The request and response model is nested and diff-oriented, making immutable
  value modeling important for readability.
* The API contract should remain visible through the existing code-first
  OpenAPI workflow described in ADR-0006 and ADR-0007.
* Java should remain the owner of persisted flag state, repositories, audit
  behavior, and the core evaluator.

## Considered Options

* Kotlin preview layer with shared Java evaluator (chosen)
* Implement preview entirely in Java
* Add preview behavior directly to the update flow
* Persist draft or proposed flag versions

## Decision Outcome

Chosen option: **Kotlin preview layer with shared Java evaluator**, because the
preview feature is read-only, response-shaping-heavy, and naturally fits
Kotlin's immutable data classes while still depending on the existing Java
domain and evaluator.

The service exposes:

* `POST /api/flags/{flagKey}/preview`

The request includes a required `proposedChange` object and a non-empty
`sampleContexts` list. Proposed fields are partial: omitted fields and `null`
values preserve the current persisted value. Empty `targetEnvironments` and
`tenantAllowlist` collections explicitly clear those collections in the
in-memory proposed flag.

The response includes the `flagKey`, a per-context `diffs` list, and an
aggregate `summary`. Each diff includes the input sample context, the evaluation
result before the proposed change, the evaluation result after the proposed
change, and `changed`. The `changed` value means the `enabled` boolean changed;
it does not count reason-only or bucket-only differences.

The Kotlin `EvaluationPreviewService` loads the current flag through the Java
`FeatureFlagService`, maps the response to the Java `FeatureFlag` domain record,
applies the proposed change in memory, and evaluates both versions through the
Java `FeatureFlagEvaluator` for each sample context. The preview method is
read-only transactional and does not call the update flow, repository save
methods, or audit event service.

Java remains responsible for persisted feature flag state, Spring Data JDBC
repositories, audit event behavior, and the production evaluation rules. Kotlin
owns preview request and response DTOs, proposed-change modeling, per-sample
diffs, and summary aggregation.

### Consequences

* Good: preview evaluation stays aligned with production evaluation because both
  paths use `FeatureFlagEvaluator`.
* Good: Kotlin data classes keep nested preview DTOs, diffs, and aggregate
  summaries compact and immutable.
* Good: the feature stays read-only and avoids introducing draft persistence or
  audit records for unsaved proposals.
* Good: OpenAPI generation continues to derive the preview contract from Spring
  MVC, Bean Validation, and focused schema annotations.
* Bad: the feature introduces a Java/Kotlin boundary that must stay explicit,
  especially when mapping `FeatureFlagResponse` back to the Java domain record.
* Bad: preview is sample-based; it does not prove the effect across every
  possible tenant, user, or environment.
* Neutral: validation annotations on Kotlin DTOs must use field targets so Bean
  Validation and springdoc inspect the intended fields.

### Confirmation

* `EvaluationPreviewController` exposes `POST /api/flags/{flagKey}/preview`.
* `EvaluationPreviewRequest` models `proposedChange` and bounded, non-empty
  `sampleContexts` with Kotlin data classes and field-targeted validation and
  schema annotations.
* `EvaluationPreviewService` maps the current `FeatureFlagResponse` to
  `FeatureFlag`, applies proposed changes in memory, and evaluates before and
  after states with `FeatureFlagEvaluator`.
* Integration tests verify preview diffs and summaries, missing-flag behavior,
  validation failures, and that preview does not persist proposed changes or
  write audit events.
* Kotlin unit tests verify rollout, kill switch, tenant allowlist, unchanged
  diffs, summary counts, environment conversion, and empty collection clearing.
* The committed OpenAPI snapshot includes the preview path and preview schemas.

## Pros and Cons of the Options

### Kotlin Preview Layer with Shared Java Evaluator

* Good: compact fit for nested request and response DTOs
* Good: keeps diff and summary aggregation close to immutable data models
* Good: reuses Java production evaluation rules without adding persistence
* Bad: requires careful Java/Kotlin DTO and enum mapping at the boundary

### Implement Preview Entirely in Java

* Good: avoids introducing another language boundary for this feature
* Good: matches the existing persisted flag and evaluator implementation
* Bad: produces more boilerplate for nested DTOs and aggregation structures
* Bad: misses the opportunity to use Kotlin where the code is mostly immutable
  response composition

### Add Preview Behavior Directly to the Update Flow

* Good: could reuse update request handling directly
* Good: would reduce the number of endpoint-specific code paths
* Bad: risks mixing read-only preview semantics with persistence and audit
  behavior
* Bad: makes it easier for preview requests to accidentally save state or emit
  audit records

### Persist Draft or Proposed Flag Versions

* Good: could support longer-lived review workflows in the future
* Good: would allow proposed states to be shared or revisited
* Bad: adds schema, lifecycle, cleanup, and audit policy complexity before the
  product needs it
* Bad: makes a lightweight before/after preview depend on additional persistence
  concepts

## More Information

* [ADR-0006: Use Code-First OpenAPI with springdoc-openapi](0006-use-code-first-openapi-with-springdoc-openapi.md)
* [ADR-0007: Generate the Committed OpenAPI Snapshot with the springdoc Gradle Plugin](0007-generate-committed-openapi-snapshot-with-springdoc-gradle-plugin.md)
* [`EvaluationPreviewController.kt`](../../service/src/main/kotlin/com/github/milez42/featureflags/preview/EvaluationPreviewController.kt)
* [`EvaluationPreviewRequest.kt`](../../service/src/main/kotlin/com/github/milez42/featureflags/preview/EvaluationPreviewRequest.kt)
* [`EvaluationPreviewService.kt`](../../service/src/main/kotlin/com/github/milez42/featureflags/preview/EvaluationPreviewService.kt)
* [`FeatureFlagEvaluator.java`](../../service/src/main/java/com/github/milez42/featureflags/flags/FeatureFlagEvaluator.java)
* [`FeatureFlagApiIntegrationTest.java`](../../service/src/test/java/com/github/milez42/featureflags/flags/FeatureFlagApiIntegrationTest.java)
* [`EvaluationPreviewServiceTest.kt`](../../service/src/test/kotlin/com/github/milez42/featureflags/preview/EvaluationPreviewServiceTest.kt)
* [`README.md`](../../README.md)
