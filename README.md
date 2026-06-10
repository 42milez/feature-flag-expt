# feature-flag-expt

English | [日本語](README.ja.md)

## Overview

[![CI](https://github.com/42milez/feature-flag-expt/actions/workflows/ci.yml/badge.svg)](https://github.com/42milez/feature-flag-expt/.github/workflows/ci.yml)

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
| `CI` | Pull requests and manual dispatches | Service formatting, Error Prone compilation, unit tests, Testcontainers-backed integration tests, Kubernetes render validation, OpenAPI snapshot drift detection, Prometheus alert rule validation, pull request Docker image buildability, and pull request Trivy image scanning |
| `Kind Smoke Test` | Daily at 18:00 UTC, which is 03:00 JST, and manual dispatches | Scheduled and manual cluster startup verification in kind, plus a Trivy scan of the built image archive after the deployment smoke check and any Kubernetes failure diagnostics |

Pull request CI validates Prometheus alert rules with `promtool` without
running a Prometheus server. It also builds the service image locally and scans
that exact image with Trivy, failing on fixed high or critical OS and library
vulnerabilities while ignoring unfixed findings. The scheduled Trivy gate can
become red when new CVEs are published even if no application code changed;
that is expected for a vulnerability gate, and ignoring unfixed findings only
reduces that risk.

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

The application source code, Kubernetes manifests, observability stack, and CI
all live in a single repository. In a production system, the deployment
configuration would typically live in a separate config repository reconciled by
a GitOps controller such as Argo CD or Flux, which allows an independent deploy
cadence, tighter access control over cluster-affecting changes, and
least-privilege credentials that keep cluster write access out of application
CI.

This repository keeps those pieces together so the validation path stays
reviewable end to end. A change can show how the application code, image build,
manifests, observability, and CI checks fit together without depending on a
second repository. For this project scope, the trade-off favors a compact
portfolio example that is easy to review as a whole over the release-boundary
separation that would matter more in an operated production platform.

The application workload uses a minimal runtime hardening posture aligned with
the Kubernetes Pod Security Standards restricted profile: non-root user and
group, dropped Linux capabilities, no service account token mount, a read-only
root filesystem with a bounded `/tmp` volume, RuntimeDefault seccomp, resource
bounds, health probes, and graceful termination. The kind and Kustomize workflow
validates the declarative deployment path and smoke-tests startup behavior; it
is not a complete production cluster security model. In real production
traffic, Kubernetes endpoint removal can still race with SIGTERM delivery, so a
rollout could add a short `preStop` delay if the platform needs extra endpoint
propagation time.

## Running the Service

### Prerequisites

A PostgreSQL instance must be running and accessible. API access uses two local
HTTP Basic users: a reader for read-style operations and an operator for create
and update operations. Prometheus metrics require any configured local user.
Swagger UI and OpenAPI docs remain publicly accessible without authentication so
the portfolio can be explored locally.
Audit events record the authenticated HTTP Basic principal as `actor` for create
and update operations. The actor is derived by the service from Spring Security
and is never accepted from request payloads.

| Variable | Local value |
|---|---|
| `FEATURE_FLAGS_DB_URL` | `jdbc:postgresql://localhost:5432/featureflags` |
| `FEATURE_FLAGS_DB_USERNAME` | `featureflags` |
| `FEATURE_FLAGS_DB_PASSWORD` | `featureflags` |
| `FEATURE_FLAGS_SECURITY_READER_USERNAME` | `featureflags-reader` |
| `FEATURE_FLAGS_SECURITY_READER_PASSWORD` | `featureflags-reader` |
| `FEATURE_FLAGS_SECURITY_OPERATOR_USERNAME` | `featureflags-operator` |
| `FEATURE_FLAGS_SECURITY_OPERATOR_PASSWORD` | `featureflags-operator` |

HTTP Basic is a local portfolio baseline for this phase. Although this
repository includes PostgreSQL for feature flag persistence, user credentials
are intentionally kept out of the application database. In this portfolio
scope, local authentication is only a deployment boundary; PostgreSQL is
reserved for flag state, rollout configuration, validation behavior, and audit
events. A production deployment would need its authentication boundary to be
evaluated against its operational and compliance context. Startup password
encoding is only for the in-memory user store; it does not protect environment
variables or Kubernetes Secrets.

Route-to-authority mappings are also intentionally kept in `SecurityConfig` for
this small portfolio service. In a production system, this model could evolve
with clearer endpoint grouping, operation-level authorities such as
`flags:read`, `flags:write`, and `metrics:read`, method security for more
complex checks, or an external authorization layer when policy decisions need
to be managed outside the application. This project keeps the mapping hardcoded
so the security boundary remains easy to inspect without extra configuration
indirection.

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

The value of a feature flag is not only in storing configuration, but in letting
an application decide whether to enable a feature for a runtime context. First,
create a flag that targets the production environment and allowlists `tenant-a`:

```bash
curl -u featureflags-operator:featureflags-operator \
  -H 'Content-Type: application/json' \
  -d '{"flagKey":"checkout-redesign","status":"ENABLED","targetEnvironments":["production"],"killSwitchActive":false,"tenantAllowlist":["tenant-a"],"rolloutPercentage":25}' \
  http://localhost:8080/api/flags
```

Next, evaluate the flag with the production environment and `tenant-a` as the
runtime context. The `enabled` and `reason` fields let the caller switch behavior
without knowing the internal structure of the flag configuration:

```bash
curl -u featureflags-reader:featureflags-reader \
  -H 'Content-Type: application/json' \
  -d '{"flagKey":"checkout-redesign","environment":"production","tenantId":"tenant-a"}' \
  http://localhost:8080/api/evaluate
```

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

Apply the opt-in local Prometheus and Grafana stack after the app dev overlay is
running:

```bash
./gradlew k8sApplyObservabilityDev
./gradlew k8sWaitObservabilityDev
./gradlew k8sStatusObservabilityDev
```

Port-forward the local observability services when you want to inspect them:

```bash
./gradlew k8sPortForwardPrometheus
./gradlew k8sPortForwardGrafana
```

Prometheus is available at `http://localhost:9090`, and Grafana is available at
`http://localhost:3000` with the dev-only placeholder credentials
`admin` / `admin`. See [docs/observability.md](docs/observability.md) for the
local verification workflow, sample traffic commands, and manual refresh steps
after changing rules or dashboards.

The local observability overlay intentionally stops at stdout/stderr logs and a
small Prometheus/Grafana stack because this repository is scoped as a portfolio
project. It does not install cluster-level log collection middleware. A
production deployment should select log collection, routing, retention, and
access-control middleware based on the target platform and operational
requirements.

## Related Information

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

Actuator health endpoints are public for probes, while Prometheus metrics
require HTTP Basic credentials from any configured local user. See
[docs/observability.md](docs/observability.md) for metric names, structured
logging, Prometheus and Grafana artifacts, and access-control expectations.

### Pack the codebase for implementation review

Use [Repomix](https://repomix.com/guide) to generate a single AI-friendly
implementation-review pack from the source, tests, API docs, deployment
manifests, and selected operational configuration:

```bash
npx repomix@1.14.1 --config repomix.config.json
```

The generated file is written to
`build/repomix/feature-flag-expt-review.xml`. Generated Repomix output is
ignored by Git. Repomix runs a security check, but that does not replace human
review. Before sharing the generated file with external AI services, review the
output for secrets, personal data, internal URLs, credentials, and
environment-specific configuration.

To inspect the largest token contributors while generating the pack, run the
same command with a token-count tree threshold. The value `1000` means "show
files and directories with at least 1000 tokens"; it is not a token limit.

```bash
npx repomix@1.14.1 --config repomix.config.json --token-count-tree 1000
```

When reviewing local work-in-progress changes, include the working tree and
staged diff explicitly:

```bash
npx repomix@1.14.1 --config repomix.config.json --include-diffs
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
