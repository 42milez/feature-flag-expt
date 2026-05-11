# feature-flag-expt

English | [日本語](README.ja.md)

## Overview

feature-flag-expt is a Spring Boot service for managing and evaluating feature
flags. It exposes REST APIs to create, read, update, evaluate, preview, and audit
flags.

Flags can be targeted by environment, controlled with an emergency kill switch,
allowlisted by tenant, and rolled out deterministically by percentage using a
stable bucket derived from the flag key and tenant or user identity. Updates are
persisted to PostgreSQL through Spring Data JDBC, and state changes are recorded
as audit events.

Most of the production flag domain, persistence flow, audit behavior, and core
evaluator are implemented in Java. The preview API is implemented in Kotlin to
model proposed changes, per-sample diffs, and summary output without saving the
change or writing audit events.

## Running the Service

### Prerequisites

A PostgreSQL instance must be running and accessible. The defaults match the credentials below:

| Variable | Default |
|---|---|
| `FEATURE_FLAGS_DB_URL` | `jdbc:postgresql://localhost:5432/featureflags` |
| `FEATURE_FLAGS_DB_USERNAME` | `featureflags` |
| `FEATURE_FLAGS_DB_PASSWORD` | `featureflags` |

### Start PostgreSQL with Docker

For local Swagger UI checks, start a PostgreSQL container with the default
database settings:

```bash
docker run --name feature-flags-postgres \
  -e POSTGRES_DB=featureflags \
  -e POSTGRES_USER=featureflags \
  -e POSTGRES_PASSWORD=featureflags \
  -p 5432:5432 \
  -d postgres:16-alpine
```

If the container already exists, start it again:

```bash
docker start feature-flags-postgres
```

The PostgreSQL container only starts the database process. Schema migrations are
applied by Flyway when the Spring Boot application starts, using the migrations
under `service/src/main/resources/db/migration`.

It is also possible to run migrations from a PostgreSQL container startup flow
with tools such as Flyway CLI or `docker-entrypoint-initdb.d`, but this project
uses Spring Boot startup as the standard migration path.

### Start the service

```bash
./gradlew :service:bootRun
```

Override the database connection if needed:

```bash
FEATURE_FLAGS_DB_URL=jdbc:postgresql://localhost:5432/featureflags \
FEATURE_FLAGS_DB_USERNAME=featureflags \
FEATURE_FLAGS_DB_PASSWORD=featureflags \
./gradlew :service:bootRun
```

### Swagger UI

Once the service is running, open the Swagger UI in a browser:

```
http://localhost:8080/swagger-ui.html
```

The raw OpenAPI spec is also available at:

| Format | URL |
|---|---|
| JSON | `http://localhost:8080/v3/api-docs` |
| YAML | `http://localhost:8080/v3/api-docs.yaml` |

A static snapshot of the spec is committed at [docs/openapi.yaml](docs/openapi.yaml).

For partial flag updates, omit a collection field, or send it as `null`, to
preserve its current value. Sending an empty `targetEnvironments` or
`tenantAllowlist` array intentionally clears that collection.

```json
{
  "targetEnvironments": null,
  "tenantAllowlist": null
}
```

```json
{
  "targetEnvironments": [],
  "tenantAllowlist": []
}
```

## Preview API

The preview endpoint evaluates a proposed feature flag change without saving it
or writing audit events:

```http
POST /api/flags/{flagKey}/preview
```

Preview is implemented in Kotlin because immutable data classes are a compact
fit for nested request/response models, per-sample diffs, and summary
aggregation. The persisted domain model, repository flow, audit behavior, and
core `FeatureFlagEvaluator` remain in Java so the production evaluation path
stays shared.

## Static Analysis

### Check formatting (Spotless)

```bash
./gradlew :service:spotlessCheck
```

### Fix formatting automatically (Spotless)

```bash
./gradlew :service:spotlessApply
```

### Run static analysis (Error Prone)

Error Prone runs automatically during compilation.

```bash
./gradlew :service:compileJava
```

## Running Tests

### Run all tests

```bash
./gradlew :service:test
```

### Run a specific test class

```bash
./gradlew :service:test --tests "com.github.milez42.featureflags.flags.FeatureFlagEvaluatorTest"
```

### Run a specific test method

```bash
./gradlew :service:test --tests "com.github.milez42.featureflags.flags.FeatureFlagEvaluatorTest.fullRolloutEnablesFlag"
```
