# feature-flag-expt

English | [日本語](README.ja.md)

[![CI](https://github.com/42milez/feature-flag-expt/actions/workflows/ci.yaml/badge.svg)](https://github.com/42milez/feature-flag-expt/actions/workflows/ci.yaml)
![Java](https://img.shields.io/badge/Java-25-orange)
![Kotlin](https://img.shields.io/badge/Kotlin-2.3-7F52FF)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0-6DB33F)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-336791)
![Kubernetes](https://img.shields.io/badge/Kubernetes-kind-326CE5)
[![License](https://img.shields.io/badge/license-MIT-blue)](LICENSE)

> **Status:** Portfolio / learning project — built to demonstrate backend
> engineering practices end to end, not a production service.

A Spring Boot feature-flag service used as a vehicle to demonstrate **JVM domain
design, a real Kubernetes deployment path, observability, and CI quality gates**
in one reviewable repository. Flags are targeted by environment, guarded by an
emergency kill switch, allowlisted per tenant, and rolled out by a deterministic
percentage bucket derived from the flag key and tenant or user identity — with
every state change recorded as an audit event.

## Table of Contents

- [What This Project Demonstrates](#what-this-project-demonstrates)
- [Architecture](#architecture)
- [Tech Stack](#tech-stack)
- [Quick Start](#quick-start)
- [API Overview](#api-overview)
- [Design Decisions (ADRs)](#design-decisions-adrs)
- [Deployment & Operations](#deployment--operations)
- [Observability](#observability)
- [Development](#development)
- [Repository Layout](#repository-layout)

## What This Project Demonstrates

- **JVM service design** — Java owns the persisted flag domain, evaluator,
  Spring Data JDBC transaction flow, audit recording, Micrometer metrics, and
  the Spring Security boundary; Kotlin owns the request/response-heavy preview
  and rollout-policy APIs, while the reusable policy validator stays in Java and
  is shared with the production update path.
  ([ADR-0008](docs/decisions/0008-use-kotlin-for-evaluation-preview-api.md))
- **Fail-closed security boundary** — local HTTP Basic with reader/operator
  roles, a public surface limited to probes and API docs, and unclassified
  `/api/**` routes denied by default.
  ([ADR-0010](docs/decisions/0010-use-http-basic-for-local-portfolio-security-boundary.md))
- **Real Kubernetes path** — Kustomize `base` and `dev` overlays deployed to
  kind, hardened to the Pod Security Standards *restricted* profile (non-root,
  read-only root filesystem, dropped capabilities, RuntimeDefault seccomp,
  graceful shutdown).
  ([ADR-0009](docs/decisions/0009-use-kind-for-local-kubernetes-development-and-ci-validation.md))
- **Observability as evidence** — Actuator/Micrometer metrics, ECS JSON
  structured logs, committed Prometheus alert rules with `promtool` rule tests,
  and a Grafana dashboard.
  ([ADR-0011](docs/decisions/0011-keep-observability-stack-alerting-ready-but-local.md))
- **CI quality gates** — formatting, Error Prone, the full and Testcontainers
  test suites, Kubernetes render validation, OpenAPI snapshot drift detection,
  `promtool` checks, and Trivy secret/image scanning on every change.

## Architecture

The flag domain, evaluator, persistence, and audit behavior are implemented in
Java. The preview and rollout-policy APIs are implemented in Kotlin to model
proposed changes, per-sample diffs, and validation results, while the reusable
rollout policy validator stays in Java and is shared with the production update
path.

```mermaid
flowchart LR
    Client([Client · curl · Swagger UI])

    subgraph Sec["Spring Security · HTTP Basic"]
        Auth{"reader / operator role"}
    end

    subgraph API["REST API · /api"]
        FlagCtl["Feature Flag &amp; Evaluate<br/><b>Java</b>"]
        PreviewCtl["Preview<br/><b>Kotlin</b>"]
        PolicyCtl["Rollout Policy<br/><b>Kotlin</b>"]
    end

    subgraph Core["Domain &amp; Services · Java"]
        Eval["Feature Flag Evaluator"]
        Svc["Feature Flag Service"]
        Policy["Rollout Policy Validator<br/>(shared)"]
        Audit["Audit Event Service"]
    end

    Repo[["Spring Data JDBC"]]
    DB[("PostgreSQL<br/>flags · audit_events")]

    Client --> Auth
    Auth --> FlagCtl & PreviewCtl & PolicyCtl
    FlagCtl --> Svc & Eval
    PreviewCtl --> Eval
    PolicyCtl --> Policy
    Svc --> Policy & Audit & Repo
    Audit --> Repo
    Repo --> DB
```

Evaluation applies the following checks in order, returning the first match as
the result `reason`:

```mermaid
flowchart TD
    Start([evaluate: flag + context]) --> S{status == DISABLED?}
    S -- yes --> R1[/false · FLAG_DISABLED/]
    S -- no --> E{environment targeted?}
    E -- no --> R2[/false · ENVIRONMENT_NOT_TARGETED/]
    E -- yes --> K{kill switch active?}
    K -- yes --> R3[/false · KILL_SWITCH_ACTIVE/]
    K -- no --> A{tenant in allowlist?}
    A -- yes --> R4[/true · TENANT_ALLOWLIST_MATCH/]
    A -- no --> I{tenant or user id present?}
    I -- no --> R5[/false · ROLLOUT_MISS/]
    I -- yes --> B{"bucket(flagKey, id) &lt; rolloutPercentage?"}
    B -- yes --> R6[/true · ROLLOUT_MATCH/]
    B -- no --> R7[/false · ROLLOUT_MISS/]
```

> `bucket` is `floorMod(SHA-256(flagKey + ":" + identity), 100)`, so the same
> flag key and identity always land in the same bucket — the rollout is stable
> and deterministic rather than random per request.

## Tech Stack

| Area | Technology |
|---|---|
| Language | Java 25 (toolchain), Kotlin 2.3 |
| Framework | Spring Boot 4.0 — Web MVC, Security, Validation, Actuator |
| Persistence | Spring Data JDBC + PostgreSQL, Flyway migrations |
| API docs | springdoc-openapi 3.0 (code-first), committed OpenAPI snapshot |
| Observability | Micrometer + Prometheus, ECS JSON logging, Grafana |
| Build | Gradle (convention plugins + version catalog) → distroless `java25` image |
| Quality | Spotless (google-java-format, ktfmt), Error Prone |
| Test | JUnit 5, MockK, Testcontainers (PostgreSQL), Spring Security Test |
| Deploy | Docker (distroless, non-root), Kubernetes + Kustomize, kind |
| CI | GitHub Actions, Trivy, promtool |

Exact patch versions are pinned in [`gradle/libs.versions.toml`](gradle/libs.versions.toml).

## Quick Start

Create and evaluate a flag in three steps. Requires Docker and JDK 25.

**1. Start PostgreSQL**

```bash
docker run --name feature-flags-postgres \
  -e POSTGRES_DB=featureflags -e POSTGRES_USER=featureflags \
  -e POSTGRES_PASSWORD=featureflags -p 5432:5432 -d postgres:16-alpine
```

The container only runs the database process; schema migrations are applied by
Flyway when the application starts. See [Configuration](#configuration) to
override the defaults.

**2. Run the service**

```bash
./gradlew :service:bootRun
```

**3. Create a flag, then evaluate it**

```bash
# Create: targets production, allowlists tenant-a, 25% rollout (operator role)
curl -u featureflags-operator:featureflags-operator \
  -H 'Content-Type: application/json' \
  -d '{"flagKey":"checkout-redesign","status":"ENABLED","targetEnvironments":["production"],"killSwitchActive":false,"tenantAllowlist":["tenant-a"],"rolloutPercentage":25}' \
  http://localhost:8080/api/flags
```

```jsonc
// 201 Created
{ "flagKey": "checkout-redesign", "status": "ENABLED",
  "targetEnvironments": ["production"], "killSwitchActive": false,
  "tenantAllowlist": ["tenant-a"], "rolloutPercentage": 25 }
```

```bash
# Evaluate for production + tenant-a (reader role)
curl -u featureflags-reader:featureflags-reader \
  -H 'Content-Type: application/json' \
  -d '{"flagKey":"checkout-redesign","environment":"production","tenantId":"tenant-a"}' \
  http://localhost:8080/api/evaluate
```

```jsonc
// 200 OK — tenant-a is allowlisted, so evaluation short-circuits before the
// percentage rollout; bucket is null because rollout logic was never reached.
{ "flagKey": "checkout-redesign", "enabled": true,
  "reason": "TENANT_ALLOWLIST_MATCH", "bucket": null }
```

The `enabled` and `reason` fields let a caller switch behavior without knowing
the internal structure of the flag configuration. Browse every endpoint
interactively at **`http://localhost:8080/swagger-ui.html`**. For the
Kubernetes/kind path, see [Deployment & Operations](#deployment--operations).

## API Overview

| Method | Path | Role | Purpose | Impl |
|---|---|---|---|---|
| `POST` | `/api/flags` | operator | Create a flag | Java |
| `GET` | `/api/flags/{flagKey}` | reader / operator | Get a flag | Java |
| `PATCH` | `/api/flags/{flagKey}` | operator | Update a flag (rollout policy enforced) | Java |
| `POST` | `/api/evaluate` | reader / operator | Evaluate a flag for a context | Java |
| `GET` | `/api/flags/{flagKey}/audit-events` | reader / operator | List audit events (oldest first) | Java |
| `POST` | `/api/flags/{flagKey}/preview` | reader / operator | Preview a proposed change (diff, no write) | Kotlin |
| `POST` | `/api/flags/{flagKey}/validate-change` | reader / operator | Validate a proposed change against rollout policy | Kotlin |

**Operational endpoints**

| Path | Access |
|---|---|
| `/actuator/health` (`/liveness`, `/readiness`) | Public (probes) |
| `/actuator/prometheus` | Authenticated (any local user) |
| `/swagger-ui.html`, `/v3/api-docs(.yaml)` | Public |
| any other `/api/**` | Denied (fail closed) |

The raw OpenAPI spec is served at `/v3/api-docs` (JSON) and `/v3/api-docs.yaml`
(YAML); a static snapshot is committed at [docs/openapi.yaml](docs/openapi.yaml).

## Design Decisions (ADRs)

Significant decisions are recorded as
[Architecture Decision Records](docs/decisions/README.md) in MADR v4 format.
Highlights:

- [ADR-0002](docs/decisions/0002-use-spring-data-jdbc-instead-of-jpa.md) — Spring Data JDBC instead of JPA/Hibernate
- [ADR-0005](docs/decisions/0005-separate-domain-records-from-persistence-entities.md) — Separate domain records from persistence entities
- [ADR-0008](docs/decisions/0008-use-kotlin-for-evaluation-preview-api.md) — Kotlin for the evaluation preview API
- [ADR-0009](docs/decisions/0009-use-kind-for-local-kubernetes-development-and-ci-validation.md) — kind for local Kubernetes and CI validation
- [ADR-0010](docs/decisions/0010-use-http-basic-for-local-portfolio-security-boundary.md) — HTTP Basic for the local security boundary

See the [full index](docs/decisions/README.md) for all records.

## Deployment & Operations

### Continuous Integration

GitHub Actions uses three workflows:

| Workflow | Trigger | Coverage |
|---|---|---|
| `CI` | Pushes to `main`, pull requests, manual dispatch | Formatting, Error Prone compilation, unit tests, Testcontainers integration tests, Kubernetes render validation, OpenAPI snapshot drift detection, Prometheus alert rule validation |
| `Image Vulnerability Scan` | Pushes to `main`, pull requests, daily at 18:00 UTC (03:00 JST), manual dispatch | Service image buildability and Trivy image scanning, kept separate from test and deploy signals |
| `Kind Smoke Test` | Daily at 18:00 UTC (03:00 JST), manual dispatch | Cluster startup verification in kind, with Kubernetes failure diagnostics on deploy failure |

Pull request CI validates Prometheus alert rules with `promtool` without running
a Prometheus server. The image workflow builds the service image locally and
scans that exact image with Trivy.

<details>
<summary>Vulnerability gate behavior</summary>

The Trivy gate fails on **fixed** high or critical OS and library
vulnerabilities while excluding unfixed findings from the failure condition. It
also publishes a non-blocking job summary that *includes* unfixed high/critical
findings, so reviewers can see risks that do not fail the gate. The scheduled
run can turn red when new CVEs are published even without an application code
change; that is expected for a vulnerability gate, and excluding unfixed
findings only reduces — not eliminates — that possibility.

</details>

### Configuration

A reachable PostgreSQL instance is required. Defaults below match the Quick
Start container, so no configuration is needed to run locally; override these
environment variables to point at a different database or change credentials.

| Variable | Local value |
|---|---|
| `FEATURE_FLAGS_DB_URL` | `jdbc:postgresql://localhost:5432/featureflags` |
| `FEATURE_FLAGS_DB_USERNAME` | `featureflags` |
| `FEATURE_FLAGS_DB_PASSWORD` | `featureflags` |
| `FEATURE_FLAGS_SECURITY_READER_USERNAME` | `featureflags-reader` |
| `FEATURE_FLAGS_SECURITY_READER_PASSWORD` | `featureflags-reader` |
| `FEATURE_FLAGS_SECURITY_OPERATOR_USERNAME` | `featureflags-operator` |
| `FEATURE_FLAGS_SECURITY_OPERATOR_PASSWORD` | `featureflags-operator` |

Schema migrations live under `service/src/main/resources/db/migration` and are
applied by Flyway on application startup, which this project uses as the
standard migration path instead of a container init flow.

### Security

API access uses two local HTTP Basic users: a **reader** for read-style
operations and an **operator** for create and update operations. Prometheus
metrics require any configured user; Swagger UI and OpenAPI docs stay public so
the portfolio can be explored locally. Audit events record the authenticated
principal as `actor`, derived by the service from Spring Security and never
accepted from request payloads.

<details>
<summary>Security model scope and evolution</summary>

HTTP Basic is a local portfolio baseline. User credentials are intentionally
kept out of the application database; PostgreSQL is reserved for flag state,
rollout configuration, validation behavior, and audit events. CSRF is disabled
because this is a stateless JSON API, but HTTP Basic remains CSRF-sensitive in
browsers, so production browser access must re-enable CSRF or replace Basic.
Startup password encoding only protects the in-memory user store, not the
environment variables or Kubernetes Secrets themselves.

Route-to-authority mappings are kept hardcoded in `SecurityConfig` so the
boundary stays easy to inspect without extra indirection. A production system
could evolve toward operation-level authorities (`flags:read`, `flags:write`,
`metrics:read`), method security, or an external authorization layer, and would
replace HTTP Basic with OIDC or another organization-managed identity provider
mapped to equivalent reader/operator authorities. See
[ADR-0010](docs/decisions/0010-use-http-basic-for-local-portfolio-security-boundary.md).

</details>

### Run on kind

The kind workflow is available through Gradle tasks and matching shell scripts
under `scripts/`. The Dockerfile copies the fixed jar name
`feature-flag-platform.jar` from the service build output.

```bash
./gradlew kindCreate          # create the local cluster (or: kindRecreate / kindDelete)
./gradlew kindLoadImage       # build the jar + image and load it into kind
./gradlew k8sRenderDev        # preview the rendered dev manifests
./gradlew devDeploy           # build, load, apply, wait, and show pod status in one step
./gradlew k8sPortForward      # forward the app service, then: ./gradlew appHealth
```

The `dev` overlay adds the local kind dependencies on top of `base`: in-cluster
PostgreSQL, local database configuration, placeholder credentials, and the local
image tag used by `kind load`. Each Gradle task has a `scripts/*.sh` equivalent
for a smaller shell-only command.

An opt-in local Prometheus/Grafana stack can be applied after the app overlay is
running:

```bash
./gradlew k8sApplyObservabilityDev   # then k8sWaitObservabilityDev / k8sStatusObservabilityDev
./gradlew k8sPortForwardPrometheus   # http://localhost:9090
./gradlew k8sPortForwardGrafana      # http://localhost:3000  (dev-only admin / admin)
```

Docker Compose is intentionally **not** provided: kind validates the real
Kubernetes deployment path — manifests, service discovery, probes, and
`kubectl apply` — that Compose cannot exercise. Database-dependent integration
tests run with Testcontainers instead. See
[ADR-0009](docs/decisions/0009-use-kind-for-local-kubernetes-development-and-ci-validation.md).

### Runtime hardening

The workload aligns with the Pod Security Standards *restricted* profile:

- Non-root user and group, no service account token mount
- Read-only root filesystem with a bounded writable `/tmp` volume
- All Linux capabilities dropped, RuntimeDefault seccomp
- Resource limits, health probes, and graceful shutdown

<details>
<summary>Hardening scope and production caveats</summary>

The kind and Kustomize workflow validates the declarative deployment path and
smoke-tests startup behavior; it is not a complete production cluster security
model. In real production traffic, Kubernetes endpoint removal can still race
with SIGTERM delivery, so a rollout could add a short `preStop` delay if the
platform needs extra endpoint-propagation time.

</details>

<details>
<summary>Why a single repository?</summary>

Application code, manifests, the observability stack, and CI all live in one
repository so the validation path stays reviewable end to end, without depending
on a second repository. In a production system, deployment configuration would
typically live in a separate config repository reconciled by a GitOps controller
such as Argo CD or Flux — enabling an independent deploy cadence, tighter access
control over cluster-affecting changes, and least-privilege credentials that
keep cluster write access out of application CI. For this portfolio scope, the
trade-off favors a compact, whole-picture example over that release-boundary
separation.

</details>

## Observability

Actuator health endpoints are public for probes, while Prometheus metrics
require HTTP Basic credentials from any configured user. See
[docs/observability.md](docs/observability.md) for metric names, structured
logging, Prometheus and Grafana artifacts, sample traffic commands, and the
manual refresh steps after changing rules or dashboards.

The committed alert rules and tests are intentionally alerting-ready local
artifacts, not a production Alertmanager, PagerDuty, or Grafana provisioning
stack (see
[ADR-0011](docs/decisions/0011-keep-observability-stack-alerting-ready-but-local.md)).
The overlay deliberately stops at stdout/stderr logs and a small
Prometheus/Grafana stack; it does not install cluster-level log collection. A
production deployment would select log collection, routing, retention, and
access-control middleware based on the target platform.

## Development

### Static analysis

```bash
./gradlew :service:spotlessCheck    # check formatting
./gradlew :service:spotlessApply    # fix formatting
./gradlew :service:compileJava      # Error Prone runs during compilation
```

### Tests

```bash
./gradlew :service:test             # all tests

# a single class or method
./gradlew :service:test --tests "com.github.milez42.featureflags.flags.FeatureFlagEvaluatorTest"
./gradlew :service:test --tests "com.github.milez42.featureflags.flags.FeatureFlagEvaluatorTest.fullRolloutEnablesFlag"
```

### Pack the codebase for review

Use [Repomix](https://repomix.com/guide) to generate a single AI-friendly
implementation-review pack from the source, tests, API docs, deployment
manifests, and selected operational configuration:

```bash
npx repomix@1.14.1 --config repomix.config.json                       # → build/repomix/feature-flag-expt-review.xml
npx repomix@1.14.1 --config repomix.config.json --token-count-tree 1000   # show files/dirs ≥ 1000 tokens
npx repomix@1.14.1 --config repomix.config.json --include-diffs        # include working-tree + staged diffs
```

Generated output is git-ignored. Repomix runs a security check, but it does not
replace human review: before sharing the file with external AI services, verify
it contains no secrets, personal data, internal URLs, credentials, or
environment-specific configuration.

## Repository Layout

```text
.
├── service/                       # Spring Boot service (Java + Kotlin)
│   └── src/main/.../featureflags/
│       ├── flags/                 # Flag domain, evaluator, persistence  (Java)
│       ├── audit/                 # Audit events                        (Java)
│       ├── policy/                # Rollout policy: validator Java, API/service Kotlin
│       ├── preview/               # Preview API                         (Kotlin)
│       └── SecurityConfig, OpenApiConfig, ...
├── deploy/
│   ├── k8s/base/                  # App Deployment + Service
│   ├── k8s/overlays/dev/          # kind: in-cluster PostgreSQL, local config
│   ├── k8s/overlays/dev-observability/   # Prometheus + Grafana + alert rules
│   └── kind/cluster.yaml
├── docs/
│   ├── decisions/                 # ADRs (MADR v4)
│   ├── observability.md
│   └── openapi.yaml               # Committed OpenAPI snapshot
├── scripts/                       # Shell equivalents of the kind/k8s Gradle tasks
├── .github/workflows/             # CI · image scan · kind smoke test
└── build-logic/                   # Gradle convention plugins
```
