# Development

English | [日本語](development.ja.md)

Full reference for running and developing locally. See the [README](../README.md)
for the project overview, architecture, API surface, and operational rationale.

## Prerequisites

Choose the smallest setup path for the workflow you want to run.

### Docker-only Quick Start

Requires a Docker environment where the `docker compose` command is available.
On macOS, use whichever option fits your environment, such as
[Docker Desktop](https://docs.docker.com/desktop/) or
[OrbStack](https://docs.orbstack.dev/install). On Linux, use Docker Engine plus
the Compose plugin or an equivalent setup.

### Local kind / Kubernetes validation

Requires `kind` and `kubectl` in addition to `docker`. The kind deployment
workflow and the local Prometheus/Grafana verification commands depend on these
tools. See the official
[kind installation](https://kind.sigs.k8s.io/docs/user/quick-start/#installation)
and [Kubernetes tools](https://kubernetes.io/docs/tasks/tools/) docs.

### Direct JVM development on the host

Requires JDK 25. [Eclipse Temurin 25](https://adoptium.net/temurin/releases/?version=25)
is recommended to match CI. This path is needed for host-side tests, `bootRun`,
OpenAPI generation, and Gradle helper tasks. The project targets macOS, Linux,
or Windows through WSL because some Gradle tasks invoke shell scripts and Unix
tools.

## Quick Start (full)

Walk through creating a flag, the emergency kill switch, and the approval
workflow as one continuous sequence. Use the
[Docker-only Quick Start prerequisites](#docker-only-quick-start); a host JDK is
not required for this path.

**1. Start the local Compose stack**

```bash
docker compose up --build -d
```

Compose builds the service image, including the Spring Boot jar, and starts the
app plus PostgreSQL. The app is bound to `127.0.0.1:8080`, and PostgreSQL is
bound to `127.0.0.1:5432`, so both ports are reachable from the local machine
only. The database has no named volume and is disposable Quick Start state. Port
`8080` conflicts with `k8sPortForward`, and port `5432` conflicts with an
existing local PostgreSQL bound to the same loopback port, so run the Compose
and kind port-forwarding paths separately.

**2. Create a flag, then evaluate it**

```bash
# Create: targets production, allowlists tenant-a, 25% rollout (operator role)
curl -u featureflags-operator:featureflags-operator \
  -H 'Content-Type: application/json' \
  -d '{"flagKey":"checkout-redesign","status":"ENABLED","targetEnvironments":["production"],"killSwitchActive":false,"tenantAllowlist":["tenant-a"],"rolloutPercentage":25}' \
  http://localhost:8080/api/flags
```

```jsonc
// 201 Created
{
  "flagKey": "checkout-redesign",
  "status": "ENABLED",
  "targetEnvironments": [
    "production"
  ],
  "killSwitchActive": false,
  "tenantAllowlist": [
    "tenant-a"
  ],
  "rolloutPercentage": 25
}
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
{
  "flagKey": "checkout-redesign",
  "enabled": true,
  "reason": "TENANT_ALLOWLIST_MATCH",
  "bucket": null
}
```

The `enabled` and `reason` fields let a caller switch behavior without knowing
the internal structure of the flag configuration. Browse every endpoint
interactively at **`http://localhost:8080/swagger-ui.html`**. For the
Kubernetes/kind path, see [Running on kind](#running-on-kind).

**3. Trigger the emergency kill switch, then read the audit trail**

Every state change is attributed and recorded. Flip the kill switch, watch it
override evaluation, and see the same action land in the audit trail.

```bash
# Emergency stop: enable the kill switch (operator role, PATCH)
curl -u featureflags-operator:featureflags-operator -X PATCH \
  -H 'Content-Type: application/json' \
  -d '{"killSwitchActive":true}' \
  http://localhost:8080/api/flags/checkout-redesign
```

```jsonc
// 200 OK — killSwitchActive is now true; other fields are preserved by the partial update.
{
  "flagKey": "checkout-redesign",
  "status": "ENABLED",
  "targetEnvironments": [
    "production"
  ],
  "killSwitchActive": true,
  "tenantAllowlist": [
    "tenant-a"
  ],
  "rolloutPercentage": 25
}
```

```bash
# Re-evaluate the allowlisted tenant-a (reader role)
curl -u featureflags-reader:featureflags-reader \
  -H 'Content-Type: application/json' \
  -d '{"flagKey":"checkout-redesign","environment":"production","tenantId":"tenant-a"}' \
  http://localhost:8080/api/evaluate
```

```jsonc
// 200 OK — the kill switch is checked before the allowlist, so even allowlisted tenant-a is turned off.
{
  "flagKey": "checkout-redesign",
  "enabled": false,
  "reason": "KILL_SWITCH_ACTIVE",
  "bucket": null
}
```

```bash
# Inspect the audit trail (reader role, oldest first)
curl -u featureflags-reader:featureflags-reader \
  http://localhost:8080/api/flags/checkout-redesign/audit-events
```

```jsonc
// 200 OK — every change is recorded with the authenticated actor; details vary by eventType.
[
  {
    "id": 1,
    "flagKey": "checkout-redesign",
    "eventType": "FLAG_CREATED",
    "actor": "featureflags-operator",
    "details": {
      /* ... */
    },
    "occurredAt": "2026-..."
  },
  {
    "id": 2,
    "flagKey": "checkout-redesign",
    "eventType": "KILL_SWITCH_ENABLED",
    "actor": "featureflags-operator",
    "details": {
      "field": "killSwitchActive",
      "oldValue": false,
      "newValue": true
    },
    "occurredAt": "2026-..."
  }
]
```

The `actor` is taken from the authenticated principal, not the request body, so
the trail cannot be forged. Higher-risk changes — expanding production exposure
or raising a production rollout by 50 points or more — go through the approval
workflow instead (an operator requests, an approver decides). The next steps run
that flow.

**Approval workflow walkthrough**

High-risk changes fail closed until an approver signs off. This continues from
the `checkout-redesign` flag created above (production, `tenant-a` allowlist,
25% rollout; the kill switch is now active from step 3). Raising its production
rollout from 25% to 80% is a +55 point jump, which the risk classifier flags as
high risk. Credentials for the `approver` user are in
[Configuration](#configuration).

**4. A high-risk change is rejected without approval**

```bash
# operator raises the rollout directly — no approval attached
curl -u featureflags-operator:featureflags-operator -X PATCH \
  -H 'Content-Type: application/json' \
  -d '{"rolloutPercentage":80}' \
  http://localhost:8080/api/flags/checkout-redesign
```

```jsonc
// 422 Unprocessable Entity — the change is blocked until it is approved.
{
  "flagKey": "checkout-redesign",
  "allowed": false,
  "violations": [
    {
      "code": "HIGH_RISK_REQUIRES_APPROVAL",
      "message": "High-risk changes require approval before rollout.",
      "severity": "ERROR"
    }
  ]
}
```

**5. The operator requests approval**

```bash
# operator opens an approval request describing the same proposed change
curl -u featureflags-operator:featureflags-operator \
  -H 'Content-Type: application/json' \
  -d '{"rolloutPercentage":80}' \
  http://localhost:8080/api/flags/checkout-redesign/approval-requests
```

```jsonc
// 201 Created — a PENDING request captures who asked, the risk, and before/after snapshots.
{
  "approvalId": "5f0a5f6e-7f24-4f4f-a426-bb534ee726bd",
  "flagKey": "checkout-redesign",
  "requester": "featureflags-operator",
  "approver": null,
  "status": "PENDING",
  "riskReasons": [
    "LARGE_PRODUCTION_ROLLOUT_INCREASE"
  ],
  "currentSnapshot": {
    /* rolloutPercentage: 25, ... */
  },
  "proposedSnapshot": {
    /* rolloutPercentage: 80, ... */
  }
}
```

**6. A different approver approves it**

```bash
# approver acts on the request id from step 2 (a requester cannot approve their own)
curl -u featureflags-approver:featureflags-approver -X POST \
  http://localhost:8080/api/flags/checkout-redesign/approval-requests/5f0a5f6e-7f24-4f4f-a426-bb534ee726bd/approve
```

```jsonc
// 200 OK — the request is now APPROVED and attributed to the approver.
{
  "approvalId": "5f0a5f6e-7f24-4f4f-a426-bb534ee726bd",
  "flagKey": "checkout-redesign",
  "requester": "featureflags-operator",
  "approver": "featureflags-approver",
  "status": "APPROVED"
  // Other response fields omitted.
}
```

**7. The operator re-applies the change with the approval**

```bash
# same operator, same proposed change, now carrying the approvalId
curl -u featureflags-operator:featureflags-operator -X PATCH \
  -H 'Content-Type: application/json' \
  -d '{"rolloutPercentage":80,"approvalId":"5f0a5f6e-7f24-4f4f-a426-bb534ee726bd"}' \
  http://localhost:8080/api/flags/checkout-redesign
```

```jsonc
// 200 OK — the rollout is applied and the approval is consumed (single use).
{
  "flagKey": "checkout-redesign",
  "status": "ENABLED",
  "targetEnvironments": [
    "production"
  ],
  "killSwitchActive": true,
  "tenantAllowlist": [
    "tenant-a"
  ],
  "rolloutPercentage": 80
}
```

The approval is bound to one proposed change and one requester: it is verified
against the recorded before/after snapshots, cannot be approved by its own
requester, and is consumed on first use. The audit trail for the flag now also
lists `APPROVAL_REQUESTED`, `APPROVAL_APPROVED`, `APPROVAL_USED`, and
`ROLLOUT_PERCENTAGE_CHANGED`. See the
[README API overview](../README.md#api-overview) for the full endpoint list.

**8. Stop the local stack**

```bash
docker compose down
```

## Configuration

The service requires PostgreSQL to start. The Compose Quick Start wires the app
container to the `postgres` service automatically; when running the JVM directly
from the host, the defaults below connect to PostgreSQL on `localhost:5432`.
Override the corresponding environment variables to use a different database or
change the username and password.

| Variable | Local value |
|---|---|
| `FEATURE_FLAGS_DB_URL` | `jdbc:postgresql://localhost:5432/featureflags` |
| `FEATURE_FLAGS_DB_USERNAME` | `featureflags` |
| `FEATURE_FLAGS_DB_PASSWORD` | `featureflags` |
| `FEATURE_FLAGS_SECURITY_READER_USERNAME` | `featureflags-reader` |
| `FEATURE_FLAGS_SECURITY_READER_PASSWORD` | `featureflags-reader` |
| `FEATURE_FLAGS_SECURITY_OPERATOR_USERNAME` | `featureflags-operator` |
| `FEATURE_FLAGS_SECURITY_OPERATOR_PASSWORD` | `featureflags-operator` |
| `FEATURE_FLAGS_SECURITY_APPROVER_USERNAME` | `featureflags-approver` |
| `FEATURE_FLAGS_SECURITY_APPROVER_PASSWORD` | `featureflags-approver` |

## Running on kind

Use the
[Local kind / Kubernetes validation prerequisites](#local-kind--kubernetes-validation)
before running the kind workflow.

```bash
./gradlew kindCreate     # create the local cluster (or: kindRecreate)
./gradlew kindLoadImage  # build the image and load it into kind
./gradlew k8sRenderDev   # optionally preview the rendered dev manifests
./gradlew devDeploy      # build, load, apply, wait, and show pod status in one step
./gradlew k8sPortForward # forward the app service
./gradlew appHealth      # check the local health endpoints
```

The `dev` overlay adds the local kind dependencies on top of `base`: in-cluster
PostgreSQL, local database configuration, placeholder credentials, and the local
image tag used by `kind load`.

Docker Compose is provided only for a simple local application runtime. kind
is the validation path for Kubernetes manifests, service discovery, probes, and
`kubectl apply`. Compose binds the app to `127.0.0.1:8080`, which conflicts with
`k8sPortForward`. Do not use the Compose and kind application exposure paths at
the same time; choose one or the other. Database-dependent integration tests
continue to run with Testcontainers. For details, see
[ADR-0009](decisions/0009-use-kind-for-local-kubernetes-development-and-ci-validation.md).

## Local development cycle

Use the [direct JVM development prerequisites](#direct-jvm-development-on-the-host)
when running the JVM on the host. With that host toolchain installed, start
only the local Compose database and run the service with `bootRun` from the
host:

```bash
docker compose up -d postgres
./gradlew :service:bootRun
```

`bootRun` keeps the application running in the foreground, so Gradle's progress
display remains at `EXECUTING`. The application is ready once
`Started FeatureFlagApplication` appears. Press `Ctrl+C` to stop it. The
database is reachable at `localhost:5432` because Compose publishes PostgreSQL
on `127.0.0.1:5432`.

The Gradle Compose tasks remain convenience wrappers around Docker Compose for
contributors who already have the host Java toolchain:

```bash
./gradlew composeConfig # validate the Compose configuration
./gradlew composeUp     # start the Compose stack through Gradle
./gradlew composeDown   # stop and remove the Compose stack
```

## Static analysis

```bash
./gradlew :service:spotlessCheck # check formatting
./gradlew :service:spotlessApply # fix formatting
./gradlew :service:compileJava   # Error Prone runs during compilation
```

## Testing

```bash
./gradlew :service:test             # all tests
./gradlew :service:jacocoTestReport # generate JaCoCo XML and HTML coverage reports

# a single class or method
./gradlew :service:test --tests "com.github.milez42.featureflags.flags.FeatureFlagEvaluatorTest"
./gradlew :service:test --tests "com.github.milez42.featureflags.flags.FeatureFlagEvaluatorTest.fullRolloutEnablesFlag"
```

## Packing the codebase for review

Use [Repomix](https://repomix.com/guide) to generate a single AI-friendly
implementation-review pack from the source, tests, API docs, deployment
manifests, and selected operational configuration:

```bash
npx repomix@1.14.1 --config repomix.config.json                         # -> build/repomix/feature-flag-expt-review.xml
npx repomix@1.14.1 --config repomix.config.json --token-count-tree 1000 # show files/dirs >= 1000 tokens
npx repomix@1.14.1 --config repomix.config.json --include-diffs         # include working-tree + staged diffs
```

Run these commands from the repository root. Generated output is git-ignored.
Repomix runs a security check, but it does not replace human review: before
sharing the file with external AI services, verify it contains no secrets,
personal data, internal URLs, credentials, or environment-specific
configuration.
