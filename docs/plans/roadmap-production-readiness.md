# Production Readiness Roadmap for JVM and Container Workload Signals

> **Status:** Accepted by the repository owner. This is the final revision adopted for implementation.

## Context

This repository is a public feature-flag service project. The near-term goal is to prioritize
production-readiness improvements that align with the project's technical goals rather than
expanding feature-flag product scope.

The strongest signals should be:

- Java, Kotlin, and Spring Boot service development on the JVM.
- Production-aware API security.
- Operational monitoring artifacts, not only service metrics.
- Container workload readiness.
- CI checks that keep API, deployment, alert-rule, and image artifacts from drifting.
- Clear documentation and ADRs that explain the engineering intent quickly.

Kubernetes remains useful, but it should stay a supporting artifact rather than becoming the main work
area. The project should avoid deep Kubernetes features that are not necessary for this refinement.

Note: This refinement emphasizes JVM web service development, production container workloads,
Kubernetes, CI/CD, Infrastructure as Code, monitoring, security, and reliability improvement.

## Review Validation

An earlier review of this plan is valid; its proposed refinement matches both Prometheus tooling
behavior and the current repository state:

- This roadmap covers Java, Kotlin, Spring Boot, production container workloads, Kubernetes, CI/CD,
  Infrastructure as Code, and monitoring tools such as Grafana and PagerDuty.
- The repository already contains both Java and Kotlin production code, so positioning the project
  as mixed production-quality JVM work is more accurate than treating Kotlin as secondary.
- The repository already has feature-flag Micrometer counters, structured logs, Prometheus sample
  configs, an observability document, and a Grafana dashboard. The observability improvement should
  therefore focus on alerting, validation, and operational explanation rather than duplicating
  existing metrics.
- `observability/prometheus/` currently contains `prometheus.local.yml` and `prometheus.k8s.yml`.
  These are scrape configuration examples, not alert rule files.
- `promtool check rules` validates Prometheus rule files. It should target files such as
  `observability/prometheus/feature-flag.rules.yml`, not the existing scrape config examples.
- Pull request CI currently runs service checks, but it does not yet validate Kubernetes rendering,
  OpenAPI snapshot drift, Prometheus alert rules, or container image scanning.
- `docs/decisions/` already contains ADRs 0001 through 0009 and has a local ADR instruction file, so
  adding one or two new ADRs for the new security and monitoring decisions fits the repository's
  existing documentation culture.
- Existing observability docs already note that `tenantId` and `userId` are excluded from metric
  tags. The observability documentation should keep describing `flag.key` as an intentional,
  currently bounded operational dimension in this service, while still naming it as a tag
  to revisit if flag cardinality grows.

## Planning Principles

- One phase has one primary task.
- Each phase should be independently reviewable.
- Prefer small, production-shaped implementation over large incomplete platform features.
- Use Java and Kotlin intentionally:
  - Keep Java for the persisted feature-flag core, transactional service flow, Spring Data JDBC
    integration, observability integration, and security configuration when that matches the current
    code shape.
  - Keep Kotlin visible for existing Kotlin web/API surfaces, DTOs, validation services, and preview
    flows.
  - Documentation should present this as production-quality mixed JVM work, not as Java replacing
    Kotlin.
- Keep Kubernetes changes shallow and focused on container workload hardening and declarative
  deployment artifacts.
- Treat Kustomize as the repository's Infrastructure as Code signal, while being clear that this is
  not a full production platform.
- Capture durable trade-offs in ADRs when the implementation introduces a new security,
  observability, or platform-operation decision.
- Do not implement full feature-flag platform features such as environment-scoped configs, approval
  workflows, SDKs, streaming, or complex rule engines during this refinement pass.

## Phase 1: Add a Spring Security Boundary

### Why

The current service exposes management and service endpoints without a service-level security
boundary. For a production-aware JVM web service, the first security signal should be that public
health checks are intentionally open while API and metrics endpoints require authentication.

This phase directly covers Spring Boot security configuration while also addressing the most visible
production-readiness gap.

### Scope

Add minimal Spring Security configuration:

- Add `spring-boot-starter-security`.
- Implement a Java `SecurityConfig`.
- Permit unauthenticated access to:
  - `/actuator/health`
  - `/actuator/health/liveness`
  - `/actuator/health/readiness`
- Require authentication for:
  - `/api/**`
  - `/actuator/prometheus`
- Use HTTP Basic with environment-configured credentials for this refinement phase.
- Avoid hard-coded production credentials.
- Document that HTTP Basic is a local-development baseline and should be replaced by OIDC or another
  organization-managed identity provider in a real deployment.

### Tests

- Unauthenticated requests to `/api/**` return `401`.
- Authenticated requests to an existing API succeed.
- Health endpoints remain accessible without authentication.
- `/actuator/prometheus` requires authentication.

### Done Criteria

- Security configuration is implemented in Java.
- Existing integration tests are updated for authenticated API calls.
- New security boundary tests cover unauthenticated and authenticated cases.
- Documentation explains the intended security posture and its limitations.
- Prometheus sample config comments mention that authenticated scrape credentials are required after
  this phase.
- Because `service/` changes, run:
  1. `./gradlew :service:spotlessCheck`
  2. `./gradlew :service:compileJava`
  3. `./gradlew :service:test`

### Out of Scope

- OAuth2/OIDC.
- JWT implementation.
- Tenant-aware authorization.
- Approval workflow.
- User management APIs.

## Phase 2: Split Read and Write API Authorization

### Why

Authentication proves caller identity, but production systems also need authorization. A small role
split shows that the API treats read, evaluation, and mutation operations differently without
building a large identity platform.

This gives readers a concrete Spring Security authorization example and shows that feature flag
mutation is treated as an operationally sensitive action.

### Scope

Introduce the smallest useful role model:

- `FLAG_READER`
- `FLAG_OPERATOR`

Authorize endpoints as follows:

- Reader or operator:
  - `GET /api/flags/{flagKey}`
  - `GET /api/flags/{flagKey}/audit-events`
  - `POST /api/evaluate`
  - preview and validation read-style endpoints, if covered by existing controllers.
- Operator only:
  - `POST /api/flags`
  - `PATCH /api/flags/{flagKey}`

Prefer route-level authorization in `SecurityConfig` unless method-level security provides clearer
tests and code locality.

### Tests

- Reader can evaluate and read a flag.
- Reader cannot create or update a flag.
- Operator can create and update a flag.
- Unauthenticated callers remain rejected.

### Done Criteria

- API read and write permissions are separated.
- Authorization behavior is covered by integration tests.
- Documentation includes the minimal role model and why it exists.
- Because `service/` changes, run:
  1. `./gradlew :service:spotlessCheck`
  2. `./gradlew :service:compileJava`
  3. `./gradlew :service:test`

### Out of Scope

- Fine-grained tenant, project, or environment authorization.
- Dynamic role storage.
- Admin screens or role management APIs.

## Phase 3: Persist Audit Actor Identity

### Why

The service already records audit events, but production change history should answer who made the
change. Adding actor identity turns audit logging from a generic event trail into operationally
useful context for incident review and change governance.

This phase should stay narrow: capture the authenticated principal on write operations and store it
with audit events.

### Scope

- Add an `actor` column to audit events through a new Flyway migration.
- Add `actor` to the audit domain model and response DTO.
- Derive actor from the authenticated Spring Security principal.
- Use a small Java component such as `CurrentActorProvider` so the service layer does not directly
  depend on raw security context access everywhere.
- Store actor on create and update audit events.
- Preserve transactional audit behavior.

### Tests

- Creating a flag as an operator stores the operator username in audit events.
- Updating a flag stores the operator username in audit events.
- Audit event responses include actor.
- Flag mutation and audit persistence remain atomic.

### Done Criteria

- Actor is persisted and exposed through the audit API.
- Tests prove actor capture on write paths.
- Documentation explains that actor is server-derived, not caller-supplied.
- Because `service/` changes, run:
  1. `./gradlew :service:spotlessCheck`
  2. `./gradlew :service:compileJava`
  3. `./gradlew :service:test`

### Out of Scope

- Approval records.
- Change request state.
- Actor impersonation.
- Full audit search.

## Phase 4: Strengthen Monitoring Artifacts and Alerting

### Why

This roadmap explicitly covers mechanisms for monitoring, security, and
reliability. The repository already has useful observability foundations: feature-flag Micrometer
counters,
structured logs, Prometheus sample configs, and a Grafana dashboard. This phase should make that work
look like an operational monitoring package rather than a set of incidental metrics.

The goal is not to introduce a full observability platform. It is to commit reviewable monitoring
artifacts that show how feature-flag behavior would be watched in production.

### Scope

- Keep the existing feature-flag evaluation, enabled/disabled, update, and kill-switch counters.
- Add one lightweight Prometheus alert rule file under `observability/prometheus/` using the
  `*.rules.yml` naming convention, for example `feature-flag.rules.yml`. This keeps rule files
  separate from existing scrape config examples such as `prometheus.local.yml` and
  `prometheus.k8s.yml`.
- Include one or more small alert rules, such as:
  - kill-switch activation observed in the recent window;
  - evaluation traffic disappeared while the service is up;
  - excessive HTTP 5xx rate, if the existing actuator metrics support a simple query.
- Ensure the Grafana dashboard references the current metric names and has panels that map to the
  alert rule intent.
- Update `docs/observability.md` to describe:
  - what operators should watch first;
  - why metric tags avoid high-cardinality values such as `tenantId` and `userId`;
  - why `flag.key` is intentionally kept as a bounded operational dimension for this
    service, and when to revisit that choice;
  - how `/actuator/prometheus` authentication from Phase 1 affects the sample Prometheus configs;
  - that PagerDuty integration is intentionally represented only as alerting-ready rules, not a
    real notification route.
- Validate the alert rule locally with `promtool check rules observability/prometheus/*.rules.yml`
  when `promtool` is available. Do not run `promtool check rules` against scrape config files;
  `promtool check config` is the separate command for Prometheus server configuration files if that
  validation is added later.

### Tests and Checks

- Verify the existing service tests still assert exported Prometheus samples.
- Run `promtool check rules observability/prometheus/*.rules.yml` if `promtool` is available
  locally.
- Manually inspect the Grafana dashboard JSON for the updated metric names.
- If only observability docs and sample configs change, skip service Gradle checks.
- If a service metric such as an evaluation latency timer is added later, run:
  1. `./gradlew :service:spotlessCheck`
  2. `./gradlew :service:compileJava`
  3. `./gradlew :service:test`

### Done Criteria

- Monitoring artifacts include metrics, logs, Prometheus scrape examples, Grafana dashboard, and at
  least one `observability/prometheus/*.rules.yml` alert-rule example.
- Documentation explains the monitoring intent in terms a reader can scan quickly.
- Documentation clearly separates alerting-ready rules from real PagerDuty routing.
- The plan does not duplicate metrics already implemented unless a clear gap is found during the
  phase.

### Out of Scope

- Production Grafana provisioning. Local kind Grafana provisioning is deferred to Phase 6.
- PagerDuty routing configuration.
- Distributed tracing.
- Log backend deployment.
- SLO/error-budget implementation.

## Phase 5: Add Minimal Container Runtime Hardening

### Why

This roadmap covers production container workload concerns. The current Kubernetes
manifest already has probes, resource requests and limits, non-root execution, and a read-only root
filesystem. This
phase should add a few high-signal hardening settings without turning Kubernetes into the main
project.

### Scope

Update the service workload manifest:

- Set `automountServiceAccountToken: false`.
- Add `capabilities.drop: ["ALL"]`.
- Add `runAsGroup` to match the non-root user model.
- Add or verify `terminationGracePeriodSeconds`.

Update Spring Boot runtime configuration if needed:

- Enable graceful shutdown.
- Add a reasonable shutdown phase timeout if the framework configuration supports it cleanly.

### Tests and Checks

- Render the dev manifest with `./gradlew k8sRenderDev`.
- Verify the rendered workload includes the hardening settings.
- Keep kind smoke-test compatibility.

### Done Criteria

- Kubernetes manifests express minimal runtime hardening.
- The service still starts in the existing kind workflow.
- Documentation explains that Kubernetes and Kustomize are used to validate the declarative
  container deployment path, not to model a full production cluster.
- If only deployment and documentation files change, skip the service Gradle checks.
- If Spring Boot runtime configuration under `service/` changes, run:
  1. `./gradlew :service:spotlessCheck`
  2. `./gradlew :service:compileJava`
  3. `./gradlew :service:test`

### Out of Scope

- NetworkPolicy.
- Ingress and TLS.
- External Secrets.
- cert-manager.
- service mesh.
- HPA.
- production RBAC design.

## Phase 6: Add Local kind Prometheus and Grafana

### Why

Phase 4 makes the monitoring artifacts reviewable, but local interactive verification is stronger
when those artifacts can be exercised against live service metrics. This phase adds a lightweight
local-only Prometheus and Grafana stack to the existing kind workflow so the service scrape,
Prometheus alert rules, and Grafana dashboard can be verified end to end.

The goal is not to introduce a production observability platform. The goal is to make local
monitoring verification easy and repeatable while keeping Kubernetes changes aligned with the
repository's existing Kustomize and Gradle-script workflow.

### Scope

Add dev-only observability Kubernetes resources:

- Prometheus `Deployment` and `Service` on port `9090`.
- Grafana `Deployment` and `Service` on port `3000`.
- Use the existing `feature-flag-platform` namespace.
- Use `ClusterIP` services only; local access goes through `kubectl port-forward`.
- Use modest local resource requests and limits, non-root settings where the images support them,
  and `emptyDir` storage.
- Pin image versions explicitly:
  - `prom/prometheus:v3.12.0`
  - `grafana/grafana-enterprise:13.0.2`

Configure Prometheus for local kind verification:

- Use a static scrape target, `feature-flag-platform:8080`, with job name
  `feature-flag-platform-kind`.
- Keep the existing Kubernetes pod-discovery sample config as documentation/sample material; do not
  add RBAC-heavy pod discovery in this phase.
- Authenticate the scrape with the dev reader credentials already generated by the dev overlay
  secret.
- Load Phase 4 alert rules from `observability/prometheus/feature-flag.rules.yml`.
- Do not add Alertmanager or notification routing.

Configure Grafana provisioning:

- Provision a Prometheus datasource named `Prometheus` that points to
  `http://prometheus:9090`.
- Provision the existing dashboard from
  `observability/grafana/dashboards/feature-flag-overview.json`.
- Store local Grafana admin placeholder credentials in a dev-only Kubernetes Secret, not inline in
  the Deployment.
- Document that the Grafana Enterprise image is used only as Grafana's default free image, not as
  adoption of Enterprise-only features.

Add local workflow commands:

- Add scripts and matching Gradle tasks:
  - `k8sApplyObservabilityDev`
  - `k8sWaitObservabilityDev`
  - `k8sStatusObservabilityDev`
  - `k8sPortForwardPrometheus`
  - `k8sPortForwardGrafana`
- Keep the existing `devDeploy` task focused on service and PostgreSQL deployment unless a later
  phase deliberately adds a larger all-in-one local verification command.
- Make the observability apply script operationally depend on the app dev overlay already being
  applied, because Prometheus scrapes the app service and reuses the dev credentials.

Avoid duplicating observability source artifacts:

- Treat `observability/prometheus/feature-flag.rules.yml` as the source of truth for alert rules.
- Treat `observability/grafana/dashboards/feature-flag-overview.json` as the source of truth for the
  dashboard.
- Have the observability apply script create or update Kubernetes ConfigMaps from those source files
  before applying Prometheus and Grafana workloads.
- Keep static provisioning files for Prometheus scrape configuration and Grafana datasource/dashboard
  providers under the dev observability manifest area.

Update documentation:

- Update `docs/observability.md` with the kind verification workflow, expected local ports,
  placeholder credentials, and sample Prometheus/Grafana checks.
- Update `README.md` and `README.ja.md` together if the new Gradle commands are documented there.
- Explain that this phase is for local interactive verification, not a production monitoring stack.

### Tests and Checks

- Render and apply the existing app dev workflow:
  1. `./gradlew k8sRenderDev`
  2. `./gradlew kindLoadImage`
  3. `./gradlew k8sApplyDev k8sWaitDev`
- Apply and wait for the local observability stack:
  1. `./gradlew k8sApplyObservabilityDev`
  2. `./gradlew k8sWaitObservabilityDev`
  3. `./gradlew k8sStatusObservabilityDev`
- Port-forward Prometheus and verify:
  - `up{job="feature-flag-platform-kind"}` returns `1`.
  - After generating an evaluation request, `feature_flag_evaluation_total` is visible.
  - `/api/v1/rules` shows the Phase 4 alert rules loaded.
- Port-forward Grafana and verify:
  - The Prometheus datasource is provisioned.
  - The `Feature Flag Overview` dashboard is provisioned.
  - After generating service traffic, dashboard panels show live data.
- Run `promtool check rules observability/prometheus/*.rules.yml` when `promtool` is available.
- Run `jq empty observability/grafana/dashboards/feature-flag-overview.json`.
- If only Kubernetes, scripts, and documentation change, skip service Gradle checks.

### Done Criteria

- Local kind can run the app, PostgreSQL, Prometheus, and Grafana together.
- Prometheus scrapes `/actuator/prometheus` through authenticated in-cluster service access.
- Prometheus loads the committed alert rule file.
- Grafana loads the committed dashboard and can query Prometheus.
- Documentation clearly separates local verification from a production observability stack.

### Out of Scope

- Helm.
- Prometheus Operator.
- Alertmanager.
- PagerDuty routing.
- Loki, Tempo, or distributed tracing backends.
- Ingress or external load balancers.
- Persistent volumes for Prometheus or Grafana.
- Production RBAC, NetworkPolicy, or multi-namespace observability design.
- Adding the observability stack to scheduled kind smoke CI by default.

## Phase 7: Add CI Quality Gates for API, Deployment, Alerting, and Image Artifacts

### Why

Production-aware projects should make important checks repeatable. The repository already runs
formatting, static analysis compilation, tests, and a scheduled kind smoke test. This phase adds
lightweight gates that prevent API contract drift, broken Kubernetes rendering, invalid alert rules,
and obvious container image vulnerabilities from slipping through.

This improves project quality without adding a full CD pipeline. This phase runs before the
documentation phase on purpose: landing the CI gates it adds, OpenAPI drift detection, Kubernetes
rendering, alert-rule validation, and image scanning, in CI first lets the documentation phase
describe the CI coverage as implemented fact rather than planned work, and avoids writing hedged
documentation that would need rewriting once these gates exist.

### Scope

Add CI checks for:

- Kubernetes render validation through `./gradlew k8sRenderDev`.
- OpenAPI snapshot drift:
  - Run `./gradlew :service:generateOpenApiDocs`.
  - Check `docs/openapi.yaml` has no uncommitted diff.
- Prometheus alert rule validation with `promtool check rules observability/prometheus/*.rules.yml`.
  - Install or download `promtool` in CI through a pinned action, pinned release, or similarly
    auditable source.
  - Validate only committed alert rule files matching `observability/prometheus/*.rules.yml`, not a
    live Prometheus server.
  - Do not include `prometheus.local.yml` or `prometheus.k8s.yml` in this gate because those files
    are scrape configuration examples, not rule files.
- Docker image build verification for pull request CI.
- Trivy image vulnerability scanning as normal scope, not as an optional stretch item.
  - Keep severity and exit-code policy pragmatic for a public repository.
  - Document any ignored vulnerability only with a concrete reason.

### Tests and Checks

- Pull request CI fails if Kubernetes manifests cannot render.
- Pull request CI fails if generated OpenAPI differs from the committed snapshot.
- Pull request CI fails if Prometheus alert rules matching `observability/prometheus/*.rules.yml`
  are syntactically invalid.
- Pull request CI builds the service image.
- Pull request CI runs Trivy against the built image.
- Existing service checks remain intact.

### Done Criteria

- CI validates service code, API contract, deployment rendering, alert-rule syntax, and image
  vulnerability posture.
- README describes the expanded CI coverage.
- Since workflow and docs changes do not necessarily touch `service/`, run service Gradle checks
  only if service files change.

### Out of Scope

- Full CD pipeline.
- Registry push.
- Production deployment.
- Required image signing.
- Mandatory SBOM publication.
- Running Prometheus, Grafana, or PagerDuty deployment in CI.
- Prometheus scrape config validation, unless `promtool check config` is added as a separate,
  explicit gate later.

## Phase 8: Rewrite Public-Facing Documentation and ADRs

### Why

The project already covers more than a feature-flag API. The documentation should make the
project's technical direction clear in the first few minutes: Java/Kotlin/Spring Boot implementation,
production container workload thinking, security boundary, monitoring artifacts, CI, Infrastructure
as Code, and explicit trade-offs.

For a near-term public roadmap milestone, this phase is as important as implementation because it
helps readers understand the project quickly. The repository already uses ADRs for architectural
decisions, so the new security and monitoring trade-offs should be recorded in the same style.

### Scope

Update `README.md` and `README.ja.md` together:

- Add a concise project positioning section near the top.
- Explain the mixed JVM implementation:
  - Java domain records
  - Java evaluator
  - Java service transaction flow
  - Java persistence with Spring Data JDBC
  - Java audit behavior
  - Java observability integration
  - Java Spring Security configuration
  - Kotlin API/DTO and validation/preview surfaces already present in the service
  - Java and Kotlin integration tests
- Explain production workload considerations:
  - health probes
  - graceful shutdown
  - non-root container
  - read-only root filesystem
  - resource requests and limits
  - Prometheus metrics
  - structured logs
  - Grafana dashboard
  - Prometheus alert-rule example
  - local kind Prometheus/Grafana verification
  - `promtool` CI validation
  - CI gates
  - Trivy image scan
  - kind smoke test
- Explain that Kustomize is used as declarative deployment/IaC coverage for the local Kubernetes
  path.
- Keep Kubernetes framing modest: kind validates the deployment path, not a full production cluster.
- Distinguish local interactive observability verification from a production observability stack.
- Keep feature-flag roadmap items separate from completed implementation.

Add one or two focused ADRs under `docs/decisions/`:

- Record why HTTP Basic is acceptable as a local-development Spring Security boundary, and why it
  should be replaced by OIDC or another organization-managed identity provider in real deployment.
- Record why this refinement adds alerting-ready Prometheus rules and dashboard documentation
  without adding production PagerDuty routing, production Grafana provisioning, or a production
  observability stack. The Phase 6 local kind Prometheus/Grafana stack should be documented as
  interactive verification only.

### Done Criteria

- README content communicates the project's technical direction clearly.
- English and Japanese READMEs stay synchronized.
- Documentation names both Java and Kotlin without implying that Kotlin is secondary.
- Documentation maps observability artifacts to monitoring and reliability work.
- Documentation explains the difference between local kind observability verification and production
  observability operations.
- New ADRs follow the existing MADR-style structure and tone under `docs/decisions/`.
- No service Gradle checks are required for documentation-only changes.

### Out of Scope

- Long-form architecture essay in the README.
- Rewriting all existing ADRs.
- Implementing feature-flag roadmap items.

## Recommended Execution Order

1. Phase 1: Add a Spring Security boundary.
2. Phase 2: Split read and write API authorization.
3. Phase 4: Strengthen monitoring artifacts and alerting.
4. Phase 5: Add minimal container runtime hardening.
5. Phase 6: Add local kind Prometheus and Grafana.
6. Phase 7: Add CI quality gates for API, deployment, alerting, and image artifacts.
7. Phase 8: Rewrite public-facing documentation and ADRs.
8. Phase 3: Persist audit actor identity.

CI quality gates are sequenced immediately before the documentation phase on purpose: bringing the
codebase to its goal state first lets the documentation describe the CI coverage, OpenAPI drift,
Kubernetes rendering, alert-rule validation, and image scanning, as implemented fact rather than
planned work, and avoids writing hedged documentation that would need rewriting afterward.

If time becomes tight, complete Phases 1, 2, 4, 5, 6, and 7 first, then Phase 8. These phases cover
the strongest near-term signals: JVM/Spring Boot security, monitoring, production container
workload signals, local observability verification, CI quality gates, and documentation that makes
the implementation easy to evaluate. Phase 3 remains useful, but it should not displace the
monitoring, container-workload, and documentation work for a near-term public roadmap milestone.

If time becomes severely constrained, the minimal public-readiness core is:

1. Phase 1: security boundary.
2. Phase 4: monitoring artifacts and alerting.
3. Phase 8: public-facing documentation and ADRs.

If local interactive verification is important, add Phase 6 before Phase 8 so the documentation can
describe the local kind Prometheus/Grafana verification path accurately. If the Phase 7 CI gates are
skipped in this constrained scenario, the documentation should frame those gates as future work
rather than implemented CI coverage.

The three-phase core is independently verifiable and maps directly to the technical direction:
secure the service boundary, show how the service would be monitored, and explain the trade-offs
clearly.

## Explicit Non-Goals for This Refinement Pass

- Environment-scoped feature flag configuration implementation.
- Full production authentication and authorization architecture.
- Approval workflow or change request system.
- OpenFeature provider or SDK support.
- Complex targeting rule engine.
- Deep Kubernetes production modeling.
- Production Prometheus or Grafana operations.
- Runtime secret manager integration.
- Real PagerDuty integration.
- Multi-service tracing.
