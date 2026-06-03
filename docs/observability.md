# Observability

The service exposes Spring Boot Actuator health endpoints and a Prometheus
scrape endpoint on the main application port. Health endpoints are public so
Kubernetes probes can call them without credentials. Prometheus metrics require
HTTP Basic authentication.

| Endpoint | Purpose | Access |
|---|---|---|
| `/actuator/health` | Overall health | Public |
| `/actuator/health/liveness` | Kubernetes liveness probe | Public |
| `/actuator/health/readiness` | Kubernetes readiness probe | Public |
| `/actuator/prometheus` | Prometheus metrics scrape endpoint | HTTP Basic |

Only `health` and `prometheus` are exposed over HTTP. The committed Kubernetes
manifests do not define an Ingress, so Actuator endpoints are not externally
routed by this repository.

Swagger UI and OpenAPI docs also remain public in this portfolio phase:
`/swagger-ui.html`, `/swagger-ui/**`, `/v3/api-docs`, `/v3/api-docs/**`, and
`/v3/api-docs.yaml`. Application APIs under `/api/**` require HTTP Basic
authentication.

HTTP Basic is a local portfolio baseline. Real deployments should replace it
with OIDC or another organization-managed identity provider.

## Local Checks

Run the service:

```bash
./gradlew :service:bootRun
```

Inspect metrics:

```bash
curl -u featureflags:featureflags -s \
  http://localhost:8080/actuator/prometheus | rg "feature_flag_"
```

When running in kind, `kubectl port-forward` or `./gradlew k8sPortForward` is
the simplest manual verification path.

## Feature Flag Metrics

Java service code records Micrometer meters with lowercase dot notation.
Prometheus exports counters with underscore names and a `_total` suffix.

| Micrometer meter | Prometheus metric | Tags |
|---|---|---|
| `feature.flag.evaluation` | `feature_flag_evaluation_total` | `flag.key`, `environment`, `reason` |
| `feature.flag.evaluation.enabled` | `feature_flag_evaluation_enabled_total` | `flag.key`, `environment`, `reason` |
| `feature.flag.evaluation.disabled` | `feature_flag_evaluation_disabled_total` | `flag.key`, `environment`, `reason` |
| `feature.flag.update` | `feature_flag_update_total` | `flag.key` |
| `feature.flag.kill.switch.enabled` | `feature_flag_kill_switch_enabled_total` | `flag.key` |

Evaluation metrics are intentionally three roadmap counters rather than one
counter with an `enabled=true/false` tag. That keeps dashboard queries direct
and matches the public operational metric names.

Metric tags must stay low-cardinality. `tenantId`, `userId`, request IDs, raw
exception messages, and other unbounded values are intentionally excluded from
metrics. `flag.key` is acceptable for this portfolio service; if flag keys later
become unbounded at high scale, revisit that tag.

## Structured Logs

Console logs use Spring Boot structured logging with ECS JSON:

```yaml
logging:
  structured:
    format:
      console: ecs
```

ECS is the repository default because it provides stable field names without
assuming a specific log backend. A real deployment may switch to `logstash` or
`gelf` if the logging platform requires that format, but each deployment should
use one format consistently.

Successful evaluations, updates, and kill switch enablement emit key-value logs.
Evaluation logs include `event`, `flagKey`, `environment`, `tenantId`,
`enabled`, `reason`, and `bucket`. `tenantId` may appear in logs for incident
debugging, but it is not used as a metric tag. `userId` is not logged by default
because it is more likely to be personally identifying.

`bucket` is the evaluator's rollout bucket from `0` to `99`. It helps explain
percentage rollout decisions during debugging and incident review without
logging raw hash inputs, hash bytes, or seed material.

Update logs include changed field names at a summary level. They do not log full
tenant allowlists.

## Prometheus

The sample configurations live under `observability/prometheus/`:

- `prometheus.local.yml` includes a direct local scrape job for
  `localhost:8080`;
- `prometheus.k8s.yml` includes a Kubernetes pod-discovery scrape job that reads
  pod annotations.

The deployment adds these pod annotations:

```yaml
prometheus.io/scrape: "true"
prometheus.io/path: "/actuator/prometheus"
prometheus.io/port: "8080"
```

These annotations are discovery hints only. They work only when Prometheus is
configured with Kubernetes service discovery, RBAC, network access, and
relabeling rules that read them. They are not access control.

Because `/actuator/prometheus` requires HTTP Basic authentication, Prometheus
must receive scrape credentials through its own configuration or secret
management path. The Kubernetes annotations do not provide those credentials.

## Grafana

The dashboard JSON is committed at
`observability/grafana/dashboards/feature-flag-overview.json`. Import it into
Grafana and select the Prometheus datasource. Panels cover evaluation rate,
enabled vs disabled evaluation rate, evaluations by reason, update rate, kill
switch events, and basic JVM/process health signals.

## Production Access Control

This phase keeps Actuator on port `8080` and adds a minimal Spring Security
boundary: health endpoints and API documentation are public, while application
APIs and Prometheus metrics require HTTP Basic authentication. Production
environments should still add stronger controls before exposing the app outside
the cluster. Acceptable controls include:

- OIDC or another organization-managed identity provider for application APIs;
- a separate management port with network rules that allow Prometheus and
  kubelet probes only;
- Kubernetes `NetworkPolicy` or ingress rules that block external access;
- a service mesh or platform firewall with equivalent restrictions.

If a future phase moves Actuator to a separate management port, revisit the
probe design. Probes on a separate management context can miss main server
failures unless main-port probe paths or equivalent checks are configured.

## Tracing Scope

This phase does not implement distributed tracing. Micrometer Tracing and
OpenTelemetry are appropriate future extensions once there is a concrete trace
backend and cross-service call path to observe.
