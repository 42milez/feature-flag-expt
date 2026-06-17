---
status: "accepted"
date: 2026-06-16
decision-makers: ["Akihiro TAKASE"]
consulted: []
informed: []
---

# Configure JVM Runtime Options for Kubernetes

## Context and Problem Statement

The service runs as a Spring Boot application in a distroless Java container on
Kubernetes. The Kubernetes manifest already defines local memory requests and
limits, but the JVM runtime behavior should make the relationship between that
container limit, JVM heap sizing, GC visibility, and out-of-memory recovery
explicit.

What JVM runtime baseline should the service image use for the local
Kubernetes portfolio deployment?

## Decision Drivers

* The JVM should size heap relative to the container memory limit.
* GC behavior should be observable from container logs.
* The selected or ergonomically chosen GC should be explicit in runtime
  documentation.
* OOM conditions should fail fast and let Kubernetes restart the Pod.
* Heap dumps should not be the default OOM response because they can contain
  sensitive in-memory values.
* The configuration should stay simple and explainable for a portfolio service.
* Workload-specific tuning should require load testing and production
  baselines.

## Considered Options

* Rely on JVM defaults.
* Set an absolute `-Xmx`.
* Use container-aware percentage-based heap sizing plus runtime safety options.
* Add heap dumps on OOM as an additional OOM diagnostic behavior.

## Decision Outcome

Chosen option: **container-aware percentage-based heap sizing plus runtime
safety options**, because it ties heap behavior to the Kubernetes memory limit,
keeps GC activity visible, and fails fast after JVM out-of-memory errors without
adding sensitive heap dump artifacts.

The service image sets:

```text
-XX:MaxRAMPercentage=75.0
-XX:+ExitOnOutOfMemoryError
-Xlog:gc*:stderr:utctime,level,tags
```

`MaxRAMPercentage=75.0` is kept as the local portfolio baseline. With the
current `512Mi` memory limit, it leaves roughly 25% for non-heap JVM and native
memory. If observed OOMKills show that headroom is too small, the percentage
should be lowered in a follow-up change backed by runtime evidence.

The baseline does not pin a GC algorithm. For the current local resource
profile, JDK 25 ergonomics select Serial GC because the container provides a
`512Mi` memory limit, a `384M` maximum heap after `MaxRAMPercentage=75.0`, and
an effective single available processor. That selection is accepted for this
portfolio deployment. The selected collector is still observable at startup
through the unified GC log line emitted by `-Xlog:gc*`. If production latency,
throughput, or CPU baselines require a different collector, add an explicit GC
option such as `-XX:+UseG1GC` in a follow-up change backed by load testing.

GC logs are written to `stderr` with `utctime` timestamps rather than `stdout`.
JVM unified logging emits GC lines as plain text and has no JSON output, while
the application logs to `stdout` as structured ECS JSON. Keeping GC text on
`stderr` keeps each stream single-format, so a log collector can route by stream
instead of inferring the format from line content. `utctime` keeps GC timestamps
correlatable with application logs and metrics in UTC.

Heap dumps are intentionally not enabled by default. They can contain
credentials and other sensitive values from memory, and the deployment uses a
read-only root filesystem.

### Consequences

* Good, because heap behavior is tied to Kubernetes memory limits.
* Good, because GC activity is available through standard container logs.
* Good, because the startup log shows the selected GC without adding another
  JVM option.
* Good, because JVM OOM errors terminate the process for Kubernetes restart
  handling.
* Good, because logs can be correlated with Prometheus JVM metrics.
* Good, because avoiding heap dumps reduces the risk of writing secrets from
  memory to disk.
* Bad, because this is not workload-specific tuning.
* Bad, because `MaxRAMPercentage` should be revisited if memory limits or
  workload characteristics change significantly.
* Bad, because the GC algorithm remains tied to JDK ergonomics unless a future
  load-tested change pins it explicitly.
* Bad, because under sustained memory pressure, fail-fast behavior may lead to
  a CrashLoopBackOff until the underlying memory pressure is fixed.

### Confirmation

* `service/Dockerfile` sets the selected `JAVA_TOOL_OPTIONS`.
* `deploy/k8s/base/app/deployment.yaml` keeps explicit application container
  memory requests and limits.
* `docs/runtime-safety.md` documents the runtime option baseline, memory-limit
  relationship, current GC selection expectation, GC log inspection, and
  Prometheus runtime queries.
* The local Prometheus rules include a tested JVM heap usage warning.
* The local Grafana dashboard includes JVM heap, JVM memory area, GC pause,
  process CPU, and process uptime panels.

## Pros and Cons of the Options

### Rely on JVM Defaults

* Good, because it keeps the image configuration minimal.
* Bad, because the intended relationship between Kubernetes limits and JVM heap
  sizing is less reviewable.
* Bad, because OOM and GC log behavior are left implicit.

### Set an Absolute `-Xmx`

* Good, because the heap maximum is easy to see.
* Bad, because it can drift from Kubernetes memory limits.
* Bad, because changing local memory limits would require a second coordinated
  JVM option change.

### Container-Aware Percentage-Based Heap Sizing Plus Runtime Safety Options

* Good, because heap sizing follows the container memory limit.
* Good, because GC logs are visible through normal container logs.
* Good, because OOM errors terminate the JVM for Kubernetes restart handling.
* Bad, because the selected percentage still needs observation and adjustment if
  workload behavior changes.
* Bad, because fail-fast OOM handling can restart the whole process for an
  otherwise isolated allocation failure.
* Bad, because GC selection remains ergonomic until runtime evidence justifies
  pinning a collector.

### Add Heap Dumps on OOM

This option is an additional diagnostic behavior that could be combined with
the selected runtime baseline, not a mutually exclusive heap sizing strategy.

* Good, because heap dumps can help diagnose memory leaks.
* Bad, because heap dumps can contain sensitive in-memory values.
* Bad, because this deployment uses a read-only root filesystem and does not
  provision a controlled dump storage path.

## More Information

* [JVM runtime safety documentation](../runtime-safety.md)
* [`service/Dockerfile`](../../service/Dockerfile)
* [`deploy/k8s/base/app/deployment.yaml`](../../deploy/k8s/base/app/deployment.yaml)
* [Observability documentation](../observability.md)
