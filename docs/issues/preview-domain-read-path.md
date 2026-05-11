# Improve the Feature Flag Domain Read Path for Evaluation Preview

## Context

`EvaluationPreviewService` needs the current persisted feature flag as a
`FeatureFlag` domain object so it can compare evaluation results before and after a
proposed in-memory change.

The current implementation obtains the flag through `FeatureFlagService.get()`, which
returns a `FeatureFlagResponse` API DTO, and then converts that response back into a
`FeatureFlag`:

```text
FeatureFlagEntity -> FeatureFlagResponse -> FeatureFlag
```

This works, but the preview use case is an internal service-to-service flow. It does
not naturally need the API response DTO. The more direct path would be:

```text
FeatureFlagEntity -> FeatureFlag
```

This note captures options for removing or reducing the unnecessary response-to-domain
conversion.

## Option 1: Keep the Current Implementation

Keep `EvaluationPreviewService` calling `FeatureFlagService.get()` and converting
`FeatureFlagResponse` to `FeatureFlag` locally.

### Pros

- Minimal code; no new public service method or component is needed.
- The current behavior is clear and covered by preview service tests.
- The conversion is small because `FeatureFlagResponse` and `FeatureFlag` currently
  have the same domain-relevant fields.
- No risk of expanding `FeatureFlagService`'s public API before another use case needs
  domain access.

### Cons

- The internal preview flow depends on an API response DTO even though it needs a
  domain object.
- The read path is conceptually indirect: `Entity -> Response -> Domain`.
- Future changes to `FeatureFlagResponse` for API presentation could accidentally affect
  preview logic.
- The local `FeatureFlagResponse.toDomain()` mapping duplicates the same conceptual
  entity-to-domain mapping already used inside `FeatureFlagService`.

## Option 2: Add a Domain Read Method to `FeatureFlagService`

Add a method such as `getForEvaluation(String flagKey)` or `getDomain(String flagKey)`
to `FeatureFlagService` that returns `FeatureFlag` directly:

```java
@Transactional(readOnly = true)
public FeatureFlag getForEvaluation(String flagKey) {
  return toDomain(findEntity(flagKey));
}
```

Then `EvaluationPreviewService` can call that method directly:

```kotlin
val before = featureFlagService.getForEvaluation(flagKey)
```

### Pros

- Replaces `Entity -> Response -> Domain` with `Entity -> Domain`.
- Reuses `FeatureFlagService`'s existing private `findEntity()` and `toDomain()` logic.
- Keeps repository access encapsulated in the feature flag service rather than exposing
  the repository to preview code.
- Smallest implementation change that gives preview the type it actually needs.
- Avoids introducing another Spring service while the read use case remains simple.

### Cons

- `FeatureFlagService` would expose both controller-facing DTO methods and internal
  domain-facing methods.
- The method name needs to communicate intended use clearly; a generic `getDomain()`
  may invite broad use before the boundary is well defined.
- If more internal read use cases appear, `FeatureFlagService` may continue to grow as a
  mixed command/query/API-facing service.
- Tests may need to distinguish API response reads from evaluation/domain reads.

## Option 3: Introduce `FeatureFlagReader` or `FeatureFlagQueryService`

Create a dedicated read-side component that owns domain reads from persistence:

```text
EvaluationPreviewService -> FeatureFlagReader -> FeatureFlagRepository
```

The reader would normalize the key, load the entity, and map it directly to
`FeatureFlag`.

### Pros

- Gives internal read use cases a clear domain-oriented dependency.
- Keeps `FeatureFlagService` focused on existing controller-facing use cases such as
  create, update, get response, evaluate, and audit.
- Makes the `Entity -> Domain` path explicit and reusable for future services.
- Provides a natural home for read-specific behavior if feature flag queries become more
  numerous or specialized.

### Cons

- Adds a new Spring component for a single current caller.
- Risks duplicating `findEntity()`, key normalization, exception behavior, or mapping
  logic unless those pieces are carefully shared.
- May be premature while the only problematic path is one small conversion in
  `EvaluationPreviewService`.
- Requires more naming and responsibility decisions than the current code likely needs.

## Option 4: Extract a Shared Mapper and Keep Service Boundaries As-Is

Introduce a mapper such as `FeatureFlagMapper` for conversions between
`FeatureFlagEntity`, `FeatureFlag`, and response DTOs. `FeatureFlagService` and any
future reader/query component could use the same mapper.

### Pros

- Reduces duplication if mappings grow or appear in more places.
- Keeps conversion rules in one place.
- Can be combined later with Option 2 or Option 3.
- Makes missing-field mapping easier to review when `FeatureFlag` evolves.

### Cons

- Does not by itself fix `EvaluationPreviewService` depending on
  `FeatureFlagResponse`; it only centralizes conversion code.
- Adds indirection for mappings that are currently straightforward.
- A mapper may be unnecessary until mappings become more complex or duplicated across
  several classes.

## Suggested Direction

For the current code size, Option 2 is the most balanced improvement if this issue is
worth addressing now. It removes the indirect `Entity -> Response -> Domain` path while
keeping the change small and preserving repository encapsulation.

Option 3 becomes more attractive if multiple internal services need domain-oriented
feature flag reads or if `FeatureFlagService` starts to feel overloaded. Option 4 is best
treated as a follow-up only if conversion logic grows enough to justify a shared mapper.

Leaving the current implementation in place is also acceptable while this remains a
single small conversion, but the response-to-domain path should be reconsidered if preview
logic grows or if `FeatureFlagResponse` starts diverging from the domain model.

## Related Files

- [`EvaluationPreviewService.kt`](../../service/src/main/kotlin/com/github/milez42/featureflags/preview/EvaluationPreviewService.kt)
- [`FeatureFlagService.java`](../../service/src/main/java/com/github/milez42/featureflags/flags/FeatureFlagService.java)
- [`FeatureFlag.java`](../../service/src/main/java/com/github/milez42/featureflags/flags/FeatureFlag.java)
- [`FeatureFlagResponse.java`](../../service/src/main/java/com/github/milez42/featureflags/flags/FeatureFlagResponse.java)
