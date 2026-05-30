# Decide Whether Actuator Should Use a Separate Management Port

## Context

The service currently exposes Actuator on the main application port. Business API
traffic and operational endpoints therefore share the same listener:

```text
http://localhost:8080/api/flags
http://localhost:8080/actuator/health
http://localhost:8080/actuator/prometheus
```

This is visible in `application.yaml`: Actuator endpoint exposure is configured, but
`management.server.port` is not set. The Kubernetes Prometheus annotations also point
to port `8080`.

This note captures the reason to consider separating the Actuator management port. The
question is not only whether a different port is possible; it is whether the deployment
needs a clearer boundary between public business traffic and operational traffic.

## Why Port Separation Might Be Needed

Actuator endpoints are operational surfaces. They are useful for health checks,
metrics, diagnostics, and incident response, but they are not business APIs. Even when
only `health` and `prometheus` are exposed, these endpoints can reveal runtime behavior
that should usually stay inside the operational boundary.

A separate management port can make that boundary easier to enforce:

```text
8080 -> business API
8081 -> Actuator health and metrics
```

With that split, platform controls can allow Prometheus, kubelet probes, or internal
operators to reach the management port while keeping external clients on the business
API port.

The need becomes stronger when the service is deployed behind public ingress, shared
load balancers, broad service discovery, or strict production network policies. In those
environments, keeping Actuator on the main port can make accidental exposure easier.

## Shared Baseline: Secure Actuator Regardless of Port Layout

Port layout is not a substitute for endpoint security. Whether Actuator stays on the
main application port or moves to a separate management port, deployments should protect
`/actuator/**` with application-layer authorization and platform controls. This is
mandatory for production and worth introducing early in development or staging so that
production hardening does not require a late security redesign.

The baseline production expectation is:

- protect non-public Actuator endpoints with Spring Security, using Actuator endpoint
  matchers rather than broad path rules where possible;
- keep `/actuator/prometheus` reachable only by the metrics collector or equivalent
  internal observability path;
- avoid treating cluster-internal access as automatically safe;
- use encrypted service-to-service traffic where the platform supports it, such as
  mesh mTLS with Istio or Linkerd, or equivalent infrastructure controls;
- keep sensitive diagnostic endpoints such as `heapdump`, `env`, and `configprops`
  disabled unless a controlled incident procedure explicitly enables them.

`/actuator/prometheus` is useful but still reveals runtime details: metric names,
JVM/process characteristics, HTTP behavior, application-specific counter names, and
label dimensions. It should not be exposed to unauthenticated external clients only
because the endpoint set is narrow.

## Option 1: Keep Actuator on the Main Application Port

Keep the current configuration and continue exposing only `health` and `prometheus` over
HTTP.

### Pros

- Minimal configuration and operational complexity.
- Kubernetes probes and Prometheus annotations already target port `8080`.
- Health checks observe the same listener used by the business API.
- Fits the current portfolio/local-development scope where Actuator is not routed by an
  Ingress in this repository.
- Avoids changing docs, runbooks, tests, manifests, and scrape configuration only for a
  boundary that may not be needed yet.

### Cons

- Business API traffic and operational traffic share the same network boundary.
- Any route, proxy, or ingress rule that forwards the main port must also deliberately
  block or protect `/actuator/**`.
- `/actuator/prometheus` can expose useful reconnaissance data if it is reachable by
  unauthenticated clients.
- Future additions such as `heapdump`, `env`, or broader diagnostics would be risky if
  exposed on the same externally reachable port.
- Access control must be path-aware rather than port-aware.

## Option 2: Move Actuator to a Separate Management Port

Set a dedicated management port, such as:

```yaml
management:
  server:
    port: 8081
```

Then update Kubernetes probes, Prometheus discovery, local docs, and runbooks to use the
new port for Actuator endpoints.

### Pros

- Creates a clear network boundary between business APIs and operational endpoints.
- Makes it easier to allow only Prometheus, kubelet probes, or internal operators to
  reach Actuator.
- Reduces the chance that `/actuator/**` is accidentally exposed through a public
  ingress or load balancer.
- Provides a safer foundation if more sensitive diagnostic endpoints are ever enabled
  temporarily in controlled environments.

### Cons

- Small code/config change, but a broad operational change.
- Kubernetes probes, Prometheus annotations, scrape config, port-forward commands,
  documentation, and tests may all need updates.
- Spring Boot's `LivenessState` and `ReadinessState` indicators still reflect
  application availability, but a separate management context does not use the same web
  infrastructure as the main application port. A probe can therefore succeed while the
  business listener cannot accept traffic.
- The deployment may need another container port, Service port, NetworkPolicy rule, and
  local verification path.

Mitigation: if probes move to the management port, also expose liveness/readiness on
the main server port with
`management.endpoint.health.probes.add-additional-paths=true` or an equivalent explicit
probe path. That property requires Spring Boot 3.2 or later; confirm the project version
before using it in future migrations.

## Suggested Direction

Keep Actuator on the main application port for the current repository phase. The
Kubernetes annotations already target `8080`, this repository does not define an
Ingress that routes Actuator externally, and changing the management port would broaden
the current issue beyond documentation and observability review.

Do not rely on the narrow endpoint set as the production security story. Actuator
authorization with Spring Security should be added regardless of whether the project
keeps one port or adopts a separate management port. This is required for production and
recommended early in development/staging to avoid a late hardening surprise.

Treat separate management port support as a production-hardening issue rather than a
small observability cleanup. If the service is deployed behind public ingress or needs
stricter network isolation, move Actuator to a dedicated port and update probes,
Prometheus scraping, manifests, runbooks, tests, the expected network boundary, and
in-cluster transport security such as service mesh mTLS in the same change. The plan
should also decide how probes verify the main application listener, using Spring Boot's
main-server liveness/readiness paths or an equivalent explicit check when Kubernetes
needs to prove the business listener is reachable.

## Related Files

- [`application.yaml`](../../service/src/main/resources/application.yaml)
- [`deployment.yaml`](../../deploy/k8s/base/app/deployment.yaml)
- [`observability.md`](../observability.md)
