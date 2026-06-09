#!/usr/bin/env bash
set -euo pipefail

kubectl -n feature-flag-platform rollout status deployment/prometheus
kubectl -n feature-flag-platform rollout status deployment/grafana
