# JVM Runtime Safety

The service image sets a small JVM runtime safety baseline for the local
Kubernetes deployment:

```text
-XX:MaxRAMPercentage=75.0
-XX:+ExitOnOutOfMemoryError
-Xlog:gc*:stdout:time,level,tags
```

`-XX:MaxRAMPercentage=75.0` keeps the Java heap sized as a percentage of the
container memory limit instead of a host-sized absolute heap. The current local
Kubernetes manifest sets the app container memory limit to `512Mi`, so this
portfolio baseline leaves roughly 25% for non-heap JVM and native memory such
as Metaspace, thread stacks, code cache, direct buffers, and GC structures.

`75.0` is a local verification baseline, not production sizing guidance. If
local kind pods begin OOMKilling frequently, or if production telemetry shows
insufficient non-heap or native headroom, lower the percentage and back the
change with observed runtime data.

The baseline intentionally does not pin a GC algorithm. With the current local
resource profile, the distroless JDK 25 runtime is expected to select Serial GC
because the container provides a `512Mi` memory limit, a `384M` maximum heap
after `MaxRAMPercentage=75.0`, and an effective single available processor.
That is accepted for this portfolio deployment because the memory and CPU limits
are small and the selected collector is visible in startup GC logs. If latency,
throughput, or CPU measurements show that the ergonomic collector is a poor fit,
pin a collector such as G1 in a follow-up change backed by load testing.

`-XX:+ExitOnOutOfMemoryError` makes JVM out-of-memory errors fail fast so
Kubernetes can restart the Pod. The image does not enable
`-XX:+HeapDumpOnOutOfMemoryError`: heap dumps can contain credentials and other
sensitive in-memory values, and this deployment uses a read-only root
filesystem.

`-Xlog:gc*:stdout:time,level,tags` writes GC activity to standard output so it
is available through normal container log collection and local `kubectl logs`.

Inspect GC logs in kind:

```bash
kubectl -n feature-flag-platform logs deploy/feature-flag-platform | rg "\[gc"
```

Confirm the selected GC from the same logs. The current baseline is expected to
emit a startup line such as `Using Serial` under the local `512Mi` memory and
`500m` CPU limits.

## Kubernetes Memory Contract

The local app Deployment keeps explicit memory requests and limits:

```yaml
resources:
  requests:
    cpu: 100m
    memory: 256Mi
  limits:
    cpu: 500m
    memory: 512Mi
```

These values are local portfolio verification values. They define the memory
contract that `MaxRAMPercentage` uses, but they are not a production capacity
model.

## Prometheus Queries

The runtime metrics used by the alert rules and dashboard are verified through
the service `/actuator/prometheus` endpoint in the Actuator integration tests.
Use these queries for local inspection:

```promql
sum(jvm_memory_used_bytes{area="heap"}) / sum(jvm_memory_max_bytes{area="heap"})
rate(jvm_gc_pause_seconds_count[5m])
rate(jvm_gc_pause_seconds_sum[5m])
process_uptime_seconds
process_cpu_usage
sum by (area) (jvm_memory_used_bytes)
```

When checking a running kind deployment manually:

```bash
kubectl -n feature-flag-platform port-forward service/feature-flag-platform 8080:8080
curl -u featureflags-reader:featureflags-reader -s \
  http://localhost:8080/actuator/prometheus \
  | rg '^(jvm_memory_used_bytes|jvm_memory_max_bytes|jvm_gc_pause_seconds|process_uptime_seconds|process_cpu_usage)'
```

For the heap ratio, confirm that `sum(jvm_memory_max_bytes{area="heap"})` is a
positive value near the expected heap maximum for the current memory limit and
`MaxRAMPercentage` setting before treating the ratio as an operational signal.
