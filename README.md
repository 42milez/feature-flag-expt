# feature-flag-expt

English | [日本語](README.ja.md)

## Overview

feature-flag-expt is a Spring Boot service for managing and evaluating feature
flags. It exposes REST APIs to create, read, update, evaluate, preview, validate
proposed rollout changes, and audit flags.

Flags can be targeted by environment, controlled with an emergency kill switch,
allowlisted by tenant, and rolled out deterministically by percentage using a
stable bucket derived from the flag key and tenant or user identity. Updates are
persisted to PostgreSQL through Spring Data JDBC, and state changes are recorded
as audit events. Rollout policy validation is enforced on updates and can also
be run ahead of time to catch unsafe production rollout changes before saving
them.

Most of the production flag domain, persistence flow, audit behavior, and core
evaluator are implemented in Java. The preview API is implemented in Kotlin to
model proposed changes, per-sample diffs, and summary output without saving the
change or writing audit events. The rollout policy API/service also uses Kotlin
for the proposed-change request flow, while the reusable policy validator remains
in Java and is shared with the production update path.

## Continuous Integration

GitHub Actions uses two workflows:

| Workflow | Trigger | Coverage |
|---|---|---|
| `CI` | Pull requests and manual dispatches | Formatting, Error Prone compilation, unit tests, and Testcontainers-backed integration tests |
| `Kind Smoke Test` | Daily at 18:00 UTC, which is 03:00 JST, and manual dispatches | Spring Boot jar packaging, service Docker image buildability, and Kubernetes manifest startup verification in a kind cluster |

Docker Compose is intentionally not provided as the main local runtime because
it would not validate Kubernetes manifests, service discovery, probes,
deployment configuration, or the `kubectl apply` workflow. Instead, kind is used
to validate the Kubernetes deployment path in local and scheduled smoke-test
environments, while database-dependent integration tests run with
Testcontainers and keep external dependencies managed by the test code. The
local Kubernetes stack favors a simple validation flow shared with CI and
behavior close to standard Kubernetes over the smallest possible
single-developer local Kubernetes experience. This choice is intentional for a
portfolio project: it keeps the local setup small while still demonstrating
CI/CD and Kubernetes deployment practices. See
[ADR-0009](docs/decisions/0009-use-kind-for-local-kubernetes-development-and-ci-validation.md)
for the local Kubernetes decision.

The Kubernetes `base` layer defines the application workload and service
contract. The `dev` overlay adds the local kind dependencies: in-cluster
PostgreSQL, local database configuration, placeholder credentials, and the local
image tag used by `kind load`.

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

### Observability

Actuator health and Prometheus metrics are exposed for local and
cluster-internal operations. See [docs/observability.md](docs/observability.md)
for metric names, structured logging, Prometheus and Grafana artifacts, and
Actuator access-control expectations. See [docs/runbook.md](docs/runbook.md)
for incident-oriented metric interpretation.

### Run on kind

The kind workflow is available through Gradle tasks and matching shell scripts.
Use Gradle when you want the standard project entry point, or run the scripts
directly from `scripts/` when you want a smaller shell-only command.

Create the local kind cluster:

```bash
./gradlew kindCreate
# or: scripts/kind-create.sh
```

Build the Spring Boot jar, build the Docker image, and load it into kind. The
Dockerfile copies the fixed jar name `feature-flag-platform.jar` from the
service build output.

```bash
./gradlew kindLoadImage
# or: scripts/kind-load-image.sh
```

If an existing kind cluster was created before the node configuration changed,
recreate it so the worker node and labels are applied:

```bash
./gradlew kindRecreate
# or: scripts/kind-recreate.sh
```

Delete the local kind cluster when it is no longer needed:

```bash
./gradlew kindDelete
# or: scripts/kind-delete.sh
```

The dev overlay provides local PostgreSQL, the local database URL, placeholder
database credentials, and the local image tag. Preview the rendered manifests
when you want to inspect the generated ConfigMap, Secret, and resource set.

```bash
./gradlew k8sRenderDev
./gradlew k8sApplyDev
# or: scripts/k8s-render-dev.sh
# or: scripts/k8s-apply-dev.sh
```

Wait for PostgreSQL and the application to become ready:

```bash
./gradlew k8sWaitDev
# or: scripts/k8s-wait-dev.sh
```

Confirm that the application and PostgreSQL pods are scheduled on the worker
node:

```bash
./gradlew k8sStatusDev
# or: scripts/k8s-status-dev.sh
```

Forward the app service and verify the health endpoints:

```bash
./gradlew k8sPortForward
# or: scripts/k8s-port-forward.sh
```

Run the health checks in a separate terminal while port forwarding is active:

```bash
./gradlew appHealth
# or: scripts/app-health.sh
```

Build, load, apply, wait, and show pod status with one command:

```bash
./gradlew devDeploy
# or: scripts/dev-deploy.sh
```

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
