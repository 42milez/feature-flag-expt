#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

namespace=feature-flag-platform

if ! kubectl -n "$namespace" get secret feature-flag-platform-secret >/dev/null 2>&1; then
  echo "feature-flag-platform-secret was not found in namespace $namespace." >&2
  echo "Apply the app dev overlay first with ./gradlew k8sApplyDev." >&2
  exit 1
fi

kubectl apply -k deploy/k8s/overlays/dev-observability
