# Runbook

Use this runbook during incidents affecting feature flag evaluation, updates,
or service availability.

## First Checks

1. Check liveness and readiness:

   ```bash
   curl -s http://localhost:8080/actuator/health/liveness
   curl -s http://localhost:8080/actuator/health/readiness
   ```

2. Check Prometheus scrape availability:

   ```bash
   curl -s http://localhost:8080/actuator/prometheus | rg "feature_flag_|http_server"
   ```

3. Collect recent structured logs for `feature_flag_evaluated`,
   `feature_flag_updated`, and `feature_flag_kill_switch_enabled`.

## Readiness or Liveness Failures

If liveness fails, the process may be unhealthy and Kubernetes should restart
it. Confirm whether restarts are increasing and collect container logs before
forcing additional restarts.

If readiness fails while liveness is healthy, the process is running but should
not receive traffic. Check database health first because readiness includes the
`db` contributor.

Evidence to collect:

- `/actuator/health/readiness` response;
- pod restart count and recent events;
- database connection errors in logs;
- recent deploy or configuration changes.

## Elevated Request Failures

Use Spring Boot HTTP server metrics to determine whether failures are isolated
to one endpoint or system-wide:

```promql
sum by (uri, status) (rate(http_server_requests_seconds_count[5m]))
```

If failures are concentrated on update endpoints, inspect rollout policy
violations and validation errors. If failures are concentrated on evaluation,
check database latency, missing flags, and recent flag changes.

## Sudden Evaluation Drops

Query:

```promql
sum(rate(feature_flag_evaluation_total[5m]))
```

A sharp drop can mean clients stopped calling the service, routing changed, the
service is not ready, or evaluations are failing before the evaluator completes.
Check HTTP request rates, readiness, and recent client or deployment changes.

## Enabled or Disabled Ratio Changes

Compare:

```promql
sum(rate(feature_flag_evaluation_enabled_total[5m]))
sum(rate(feature_flag_evaluation_disabled_total[5m]))
sum by (reason) (rate(feature_flag_evaluation_total[5m]))
```

A disabled spike by `KILL_SWITCH_ACTIVE` usually means an emergency switch was
enabled. A disabled spike by `ENVIRONMENT_NOT_TARGETED` can indicate callers are
sending an unexpected environment. A disabled spike by `ROLLOUT_MISS` may be
normal during a low rollout percentage, but check whether the rollout was
recently changed.

Use evaluation logs to inspect `flagKey`, `environment`, `tenantId`, `reason`,
and `bucket` for representative requests. Do not require `userId` in logs for
normal incident review.

## Kill Switch Enabled Events

Query:

```promql
sum(increase(feature_flag_kill_switch_enabled_total[15m]))
```

If this increases unexpectedly:

- identify the `flag.key` label;
- inspect `feature_flag_kill_switch_enabled` logs;
- review audit events for the same flag;
- confirm whether the change was intentional before disabling the switch.

Do not change flags during an incident until you have collected the current flag
state, recent audit events, relevant structured logs, and the metric window that
shows the impact.

## Database Health

Readiness includes database health. If readiness reports a database issue,
check:

- database pod or managed database availability;
- credentials and JDBC URL configuration;
- connection timeout or authentication errors in logs;
- recent schema migration failures.

Feature flag updates and evaluations both read persisted flag state, so database
issues can affect both control-plane and evaluation paths.

## Management Endpoint Exposure

`/actuator/prometheus` is intended for local and cluster-internal scraping only.
Signs of unintended exposure include:

- public ingress or load balancer routes matching `/actuator/**`;
- Prometheus metrics visible from outside the expected network;
- internet scanner traffic in access logs for Actuator paths.

If exposure is suspected, restrict ingress or network policy first, then verify
that health probes and Prometheus scraping still work from the intended internal
network.
