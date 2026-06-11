---
status: "accepted"
date: 2026-06-11
decision-makers: ["Akihiro TAKASE"]
consulted: []
informed: []
---

# Keep Observability Stack Alerting-Ready but Local

## Context and Problem Statement

The service exposes Actuator health, Prometheus metrics, structured logs, and
feature-flag-specific Micrometer counters. The repository also includes a local
kind deployment path and an opt-in Prometheus/Grafana overlay.

The `deploy/k8s/overlays/dev-observability` overlay contains the runnable local
Prometheus/Grafana stack, including its local Prometheus configuration, alert
rules, rule tests, dashboard provisioning, and dashboard JSON.

The portfolio should show that the application is observable and that alert
rules can be validated, but it should not imply that this repository operates a
production monitoring platform with notification routing, durable storage, or
environment-specific incident response.

How far should the repository take the observability stack?

## Decision Drivers

* The service should expose useful metrics and structured logs.
* Alert rules should be committed, reviewable, and tested in CI.
* The Grafana dashboard should be usable in local kind verification.
* Local Kubernetes validation should remain small and reproducible.
* The repository should avoid claiming production PagerDuty, Alertmanager,
  durable Prometheus, or production Grafana provisioning.
* Detailed verification steps should live in observability documentation rather
  than overloading the README.

## Considered Options

* Alerting-ready local Prometheus/Grafana stack
* Full production observability stack
* Metrics-only implementation without rules or dashboards
* Documentation-only observability without runnable artifacts

## Decision Outcome

Chosen option: **alerting-ready local Prometheus/Grafana stack**, because it
proves the service emits operational signals and that the committed alert rules
can be validated without pretending to operate a production monitoring system.

The repository commits:

* service metrics and structured ECS JSON logging configuration;
* a local Prometheus scrape configuration under the local dev-observability
  overlay;
* canonical Prometheus alert rules and rule tests under the local
  dev-observability overlay;
* a Grafana dashboard provisioned by the local overlay;
* documentation for local traffic generation, Prometheus queries, dashboard
  inspection, and manual refresh after rule or dashboard changes.

This is intentionally not a production observability stack. The repository does
not install Alertmanager, define notification routing, configure PagerDuty or
another receiver, provide production Grafana provisioning, add durable
Prometheus storage, install cluster-level log collection, or model
environment-specific alert thresholds. Those choices depend on the operating
platform, traffic baseline, incident-management process, and access-control
model.

### Consequences

* Good, because reviewers can inspect concrete metrics, alert rules, rule tests,
  dashboard JSON, and local Kubernetes wiring.
* Good, because CI validates alert rule syntax and expected alert behavior with
  `promtool`.
* Good, because the local kind overlay can prove Prometheus and Grafana can read
  the committed artifacts.
* Good, because the README can point to one detailed observability workflow
  instead of duplicating long operational instructions.
* Bad, because notification routing, paging behavior, retention, and production
  dashboard provisioning remain out of scope.
* Bad, because alert thresholds are examples for local portfolio verification,
  not production SLO-backed thresholds.
* Neutral, because production teams can keep the application-level signal names
  while replacing the local stack with their own platform observability tools.

### Confirmation

* `service/src/main/resources/application.yaml` exposes only Actuator `health`
  and `prometheus` endpoints over HTTP and enables ECS structured console
  logging.
* `deploy/k8s/base/app/deployment.yaml` adds Prometheus scrape annotations to
  the app pod template.
* `deploy/k8s/overlays/dev-observability/prometheus/prometheus.yaml` defines the
  local Prometheus scrape configuration for `/actuator/prometheus`.
* The canonical alert rules are committed at
  `deploy/k8s/overlays/dev-observability/prometheus/feature-flag.rules.yaml`.
* The alert rule test artifact is committed at
  `deploy/k8s/overlays/dev-observability/prometheus/feature-flag.rules.test.yaml`.
* The Grafana dashboard is committed at
  `deploy/k8s/overlays/dev-observability/grafana/dashboards/feature-flag-overview.json`.
* `deploy/k8s/overlays/dev-observability/kustomization.yaml` packages
  Prometheus configuration, alert rules, Grafana datasource provisioning,
  Grafana dashboard provider provisioning, and dashboard JSON into local
  ConfigMaps.
* `docs/observability.md` documents the local Prometheus/Grafana workflow,
  metrics, structured logs, alerting scope, Grafana dashboard, production access
  control notes, and tracing scope.
* `.github/workflows/ci.yaml` runs `promtool check rules` against the canonical
  rule file and `promtool test rules` against the rule test file.

## Pros and Cons of the Options

### Alerting-Ready Local Prometheus/Grafana Stack

* Good, because it gives the portfolio concrete operational artifacts that can
  be rendered, tested, and inspected locally.
* Good, because it keeps production-specific routing and retention choices out
  of the repository.
* Good, because `promtool` validation provides CI signal without running a
  Prometheus server.
* Bad, because it does not prove real notification delivery or production
  incident workflows.
* Bad, because local dashboard provisioning is not the same as managed
  production Grafana provisioning.

### Full Production Observability Stack

* Good, because it would prove notification delivery, incident-response wiring,
  production dashboard provisioning, retention, and log collection in the real
  operating environment.
* Bad, because those decisions depend on a real operating environment and would
  be mostly speculative in this portfolio repository.
* Bad, because it would add significant setup burden to local and CI workflows.

### Metrics-Only Implementation Without Rules or Dashboards

* Good, because it would keep the repository smaller.
* Bad, because reviewers would need to infer alerting and dashboard intent from
  raw metrics alone.
* Bad, because CI would not validate alert rule behavior.

### Documentation-Only Observability Without Runnable Artifacts

* Good, because it would avoid local Kubernetes observability dependencies.
* Bad, because it would not prove that Prometheus can load the rules or that
  Grafana can render the dashboard.
* Bad, because the observability story would become aspirational rather than
  evidence-backed.

## More Information

* [Observability documentation](../observability.md)
* [`feature-flag.rules.yaml`](../../deploy/k8s/overlays/dev-observability/prometheus/feature-flag.rules.yaml)
* [`feature-flag.rules.test.yaml`](../../deploy/k8s/overlays/dev-observability/prometheus/feature-flag.rules.test.yaml)
* [`feature-flag-overview.json`](../../deploy/k8s/overlays/dev-observability/grafana/dashboards/feature-flag-overview.json)
* [`prometheus.yaml`](../../deploy/k8s/overlays/dev-observability/prometheus/prometheus.yaml)
* [`deploy/k8s/overlays/dev-observability`](../../deploy/k8s/overlays/dev-observability)
* [CI workflow](../../.github/workflows/ci.yaml)
* [`README.md`](../../README.md)
