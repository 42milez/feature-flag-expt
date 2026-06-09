#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

namespace=feature-flag-platform

if ! kubectl -n "$namespace" get secret feature-flag-platform-secret >/dev/null 2>&1; then
  echo "feature-flag-platform-secret was not found in namespace $namespace." >&2
  echo "Apply the app dev overlay first with ./gradlew k8sApplyDev." >&2
  exit 1
fi

kubectl -n "$namespace" create configmap feature-flag-prometheus-rules \
  --from-file=feature-flag.rules.yml=observability/prometheus/feature-flag.rules.yml \
  --dry-run=client -o yaml | kubectl apply -f -

kubectl -n "$namespace" create configmap feature-flag-grafana-dashboard \
  --from-file=feature-flag-overview.json=observability/grafana/dashboards/feature-flag-overview.json \
  --dry-run=client -o yaml | kubectl apply -f -

kubectl apply -k deploy/k8s/overlays/dev-observability
