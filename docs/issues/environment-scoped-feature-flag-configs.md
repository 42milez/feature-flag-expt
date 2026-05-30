# Introduce Environment-Scoped Feature Flag Configurations

## Context

The current feature flag model stores one operational configuration per `flagKey`:

```text
FeatureFlag
  flagKey
  status
  targetEnvironments
  killSwitchActive
  tenantAllowlist
  rolloutPercentage
```

`targetEnvironments` currently answers whether a flag may be evaluated in a given
environment. The rest of the configuration is shared across all targeted environments.
For example, a flag targeted to both `staging` and `production` uses the same status,
kill switch state, tenant allowlist, and rollout percentage in both environments.

That model is simple, but it does not match how production-oriented feature flag systems
are usually operated. In systems such as LaunchDarkly, a flag key identifies the feature,
while each environment owns its own serving configuration. The same flag can therefore
be fully enabled in staging, partially rolled out in production, and disabled in
development without creating different flag keys.

This project should also decide how closely it wants to align with OpenFeature. OpenFeature
is a CNCF Incubating project and a vendor-neutral specification for feature flag
evaluation APIs. This service does not need to become an OpenFeature SDK, but its
evaluation context, result shape, provider boundary, and future SDK-facing APIs should
avoid choices that make OpenFeature compatibility unnecessarily difficult.

This note describes a why-first implementation plan for moving toward:

```text
flagKey + environment -> status, rolloutPercentage, rules, killSwitch
```

## Why Change the Model

Environment is currently treated as a target constraint. It should become a
configuration boundary because rollout decisions, operational risk, approval workflows,
and access-control requirements differ by environment.

The current model makes these common states awkward or impossible to represent cleanly:

- `staging` is at 100% while `production` is at 5%.
- `production` has the kill switch active while `staging` remains enabled for debugging.
- `production` requires a tenant allowlist while `staging` can be open to all test
  tenants.
- production rollout policy checks apply only to the production configuration, not to
  the whole flag.
- audit events need to say which environment changed, not only which flag changed.
- authorization needs to distinguish low-risk staging changes from production changes
  that require elevated permission or approval.

Keeping a single shared configuration encourages accidental coupling. A safe staging
change can become a production change because both environments read the same
`rolloutPercentage`, `status`, or `killSwitchActive` value.

The goal is not only feature parity with a commercial flag provider. The goal is to make
the domain model express the operational truth: environments are independent rollout
surfaces.

## Target Model

Split feature identity from environment-specific serving configuration:

```text
FeatureFlag
  flagKey
  metadata

FeatureFlagEnvironmentConfig
  flagKey
  environment
  status
  killSwitchActive
  tenantAllowlist
  rolloutPercentage
  rules (future)
```

`FeatureFlag` should answer "what feature is this?".
`FeatureFlagEnvironmentConfig` should answer "how should this feature behave in this
environment?".

The evaluator should no longer check whether `targetEnvironments` contains the request
environment. Instead, the read path should load the environment config for
`flagKey + environment`. If no config exists, evaluation should return a clear disabled
result such as `ENVIRONMENT_NOT_CONFIGURED` or keep the existing
`ENVIRONMENT_NOT_TARGETED` reason if API compatibility is more important.

Rules should remain a future capability until there is a concrete targeting model. This
migration should reserve a domain concept for rules, but it should not design rule
storage before the project has rule semantics to persist.

## Implementation Plan

### Phase 1: Establish the Environment Config Domain

Introduce a domain object such as `FeatureFlagEnvironmentConfig` and adjust evaluation
internals to evaluate that config instead of the whole `FeatureFlag`.

Why this comes first:

- It makes the core domain language explicit before database and API changes make the
  migration wider.
- It keeps the first step small enough to test with existing fixtures.
- It separates the hardest design decision from persistence mechanics.
- It gives the evaluator a stable target shape: one environment-specific config in,
  one evaluation result out.
- It creates a natural provider boundary if the project later exposes an OpenFeature
  provider or SDK-facing evaluation API.

Expected direction:

```text
FeatureFlagService.evaluate()
  -> find current FeatureFlagEntity
  -> derive FeatureFlagEnvironmentConfig for request.environment
  -> FeatureFlagEvaluator.evaluate(config, context)
```

During this phase, the derived config can still be built from the existing shared
fields. If the requested environment is not in `targetEnvironments`, return the current
disabled behavior.

OpenFeature alignment should be considered here, but not implemented by default. The
immediate goal is to avoid incompatible naming and shape decisions. The key OpenFeature
concepts to compare against are `EvaluationContext`, `ResolutionDetails` for reason,
variant, and value metadata, and `ProviderEvents`. Naming does not need to match exactly,
but divergence should be intentional.

### Phase 2: Move Policy, Authorization, and Input Validation to Environment Configs

Change rollout policy validation so production-specific rules inspect the proposed
production config, not a flag-level `targetEnvironments` set. Add an explicit
authorization design before exposing environment-scoped writes.

Why this should happen before the schema and API migration:

- The policy package already encodes production risk, so it should reveal whether the
  new domain shape is expressive enough.
- It prevents the schema migration from carrying forward flag-level assumptions.
- It clarifies whether update requests are changing one environment or several.
- Authorization cannot be safely bolted on after production write endpoints exist.

The production 0% to 100% check should become a comparison between current and proposed
production configs. The "production access without allowlist requires a reason" rule
should also apply to the production config only.

The authorization model should decide who can:

- create or delete a production environment config;
- change production `status`, `rolloutPercentage`, or `killSwitchActive`;
- add tenants to a production allowlist;
- bypass or satisfy approval requirements for high-risk changes;
- make non-production changes without elevated permission.

Tenant identifiers should remain bounded and validated at the API edge. Environment
scoping makes the allowlist safer semantically, but it does not remove the need for
explicit size limits, non-blank values, length limits, and any tenant ID format rules
the service needs before persistence.

Environment identifiers should be validated with the same intent before they are used as
path parameters or persisted config keys. The allowed environment set should stay bounded
and intentional, whether it remains an enum or later becomes database-managed.

### Phase 3: Add Environment-Scoped Persistence, Audit, and Read-Path Caching

Add tables that store one configuration row per `flagKey + environment`, with child
tables for environment-scoped allowlists. In the same migration area, make audit records
environment-aware and decide whether the evaluation read path needs caching.

Why these changes belong together:

- The current Spring Data JDBC aggregate stores config fields directly on
  `FeatureFlagEntity`; changing that aggregate affects create, update, get, evaluate,
  audit, tests, and migrations at once.
- Audit events should describe the same persistence boundary that changed.
- Evaluation is the hot path; changing the read model without considering cache behavior
  can force a second redesign under load.
- A focused migration phase makes data backfill explicit and reviewable.

Candidate tables:

```text
feature_flags
  flag_key primary key

feature_flag_environment_configs
  flag_key references feature_flags(flag_key)
  environment
  status
  kill_switch_active
  rollout_percentage
  primary key (flag_key, environment)

feature_flag_environment_tenant_allowlist
  flag_key
  environment
  tenant_id
  primary key (flag_key, environment, tenant_id)
```

For migration from the current schema, create one environment config for each existing
`feature_flag_target_environments` row and copy the current shared status, kill switch,
rollout percentage, and tenant allowlist into each environment config.

Audit event details for status, rollout, kill switch, allowlist, and future rule changes
should include `environment`. For production changes, the audit design should also decide
whether the backing store needs append-only or tamper-evident properties. At minimum,
the application should avoid update-in-place audit records for operational events that
may be needed during incident review.

Caching should be an explicit decision, not an accidental byproduct of repository access.
Acceptable first options include:

- no cache while traffic is low, with clear read-path measurements;
- a small TTL cache for `flagKey + environment` configs;
- write-through or explicit invalidation after management API updates;
- pub/sub invalidation if multiple service instances need fast propagation.

The cache decision should define freshness expectations for production kill switch
changes. A kill switch that can remain stale for a full TTL may not meet operational
and incident-response expectations.

### Phase 4: Redesign Management APIs and Observability Around Environment Updates

Add or migrate endpoints so callers can update a single environment config directly.
Keep evaluation logs and metrics environment-aware, and add environment dimensions to
update and kill-switch telemetry where cardinality remains bounded. Decide the
authentication posture for evaluation and read endpoints in the same API design pass:
they should either be intentionally public for client-side SDK use or require service
authentication for server-side and internal use.

Why API and observability changes should follow the domain and schema:

- The public contract should expose the model after the model is proven internally.
- Existing clients may depend on flag-level create and patch behavior.
- Environment-scoped endpoints make operational intent clearer and reduce accidental
  multi-environment changes.
- Logs, metrics, and API responses should all describe the same unit of change.

Candidate endpoint shape:

```text
GET   /api/flags/{flagKey}
GET   /api/flags/{flagKey}/environments/{environment}
PATCH /api/flags/{flagKey}/environments/{environment}
POST  /api/flags/{flagKey}/environments/{environment}
DELETE /api/flags/{flagKey}/environments/{environment}
```

The existing `POST /api/flags` can either:

- create the flag identity plus one or more initial environment configs; or
- create only the flag identity and require separate environment config creation.

The first option is easier for current clients. The second option is cleaner if the
project wants a stronger separation between feature metadata and serving config.

`FeatureFlagService.evaluate()` already records `environment` on logs and evaluation
counters. Once configuration is environment-scoped, that tag becomes more semantically
accurate. The cardinality risk is mostly from `flag.key`, not from a bounded environment
enum. If environments become database-managed later, the project should still keep the
allowed environment set bounded and intentional.

### Phase 5: Update Evaluation Preview and Tests

Make evaluation preview compare current and proposed environment configs rather than
reconstructing a whole flag-level domain object.

Why this belongs near the end:

- Preview depends on the same read and evaluation path as normal evaluation.
- It is a good integration test for whether the new domain model is coherent.
- Updating it too early could create temporary adapter code that disappears after the
  schema and API changes.

Test coverage should include:

- staging at 100% and production at 0% for the same flag key;
- production kill switch active while staging remains enabled;
- production allowlist rules not leaking into staging;
- missing environment config returning a disabled result;
- production policy validation applying only to production config changes;
- production write authorization denying callers without the required role or approval;
- deleting a production environment config being denied without elevated permission;
- evaluation returning a disabled result after its environment config is deleted;
- tenant allowlist validation rejecting oversized, blank, malformed, or excessive input;
- audit records including the changed environment.

## Compatibility Strategy

Prefer a staged compatibility path over a single breaking rewrite.

Why compatibility matters:

- The current API, tests, and OpenAPI schemas assume flag-level config fields.
- A single rewrite would mix domain design, persistence migration, API redesign, audit
  changes, and policy changes in one large review.
- Keeping old request and response shapes temporarily makes it easier to verify behavior
  after each phase.

A practical compatibility approach is:

- keep the current `FeatureFlagResponse` initially, but populate it from environment
  configs;
- keep `targetEnvironments` as a derived list of configured environments during the
  transition;
- document that shared fields are transitional if they no longer have one canonical
  value across environments;
- introduce environment-specific responses before removing or redefining flag-level
  fields.

## Open Decisions

- Should a missing environment config be a 404, a disabled evaluation result, or the
  existing `ENVIRONMENT_NOT_TARGETED` reason?
- Should `POST /api/flags` create default configs for all known environments, or only
  the configs supplied by the caller?
- Should tenant allowlists remain environment-scoped, or should there also be global
  flag-level allowlists?
- Should rollout bucketing include environment in the hash input? Keeping the current
  `flagKey + identity` hash preserves a user's bucket across environments; adding
  environment makes each environment independently bucketed.
- Should production approval state be supplied by clients temporarily, or moved to a
  server-verified approval workflow before this migration?
- Should the service expose an OpenFeature provider or only keep the internal model
  compatible with OpenFeature concepts?
- Should evaluation and read endpoints be intentionally public for client-side SDKs, or
  require service-to-service authentication?
- Should config freshness use no cache, TTL caching, explicit write invalidation, or
  pub/sub invalidation across service instances?
- Should audit logs for production changes be stored in an append-only or tamper-evident
  store?
- When rules are introduced, should they be stored as structured relational rows, JSON
  policy documents, or a sealed domain model serialized through Jackson?

## Suggested Direction

Treat this as a domain-model migration, not a small evaluator cleanup. The most important
reason to do it is to prevent environment coupling: production, staging, and development
are different operational surfaces and need independent serving configurations.

Start with `FeatureFlagEnvironmentConfig` in the domain and evaluator. Move policy,
authorization, and tenant input validation to that environment-scoped concept before
exposing environment-scoped write APIs. Then migrate persistence, audit, read-path cache
behavior, APIs, observability, preview, and tests in small reviewable steps.

Defer rule storage until the service has concrete rule semantics. Keeping `rules` in the
target model is useful as a direction marker, but designing tables for non-existent rule
behavior would make this migration larger and less certain than it needs to be.

## Related Files

- [`FeatureFlag.java`](../../service/src/main/java/com/github/milez42/featureflags/flags/FeatureFlag.java)
- [`FeatureFlagEntity.java`](../../service/src/main/java/com/github/milez42/featureflags/flags/FeatureFlagEntity.java)
- [`FeatureFlagEvaluator.java`](../../service/src/main/java/com/github/milez42/featureflags/flags/FeatureFlagEvaluator.java)
- [`FeatureFlagService.java`](../../service/src/main/java/com/github/milez42/featureflags/flags/FeatureFlagService.java)
- [`CreateFeatureFlagRequest.java`](../../service/src/main/java/com/github/milez42/featureflags/flags/CreateFeatureFlagRequest.java)
- [`UpdateFeatureFlagRequest.java`](../../service/src/main/java/com/github/milez42/featureflags/flags/UpdateFeatureFlagRequest.java)
- [`RolloutPolicyValidator.java`](../../service/src/main/java/com/github/milez42/featureflags/policy/RolloutPolicyValidator.java)
- [`V1__create_feature_flag_tables.sql`](../../service/src/main/resources/db/migration/V1__create_feature_flag_tables.sql)
