#!/usr/bin/env bash
set -euo pipefail

namespace=feature-flag-platform

kubectl -n "$namespace" get pods -o wide
kubectl -n "$namespace" get services
kubectl -n "$namespace" get events \
  --field-selector type=Warning \
  --sort-by=.lastTimestamp
