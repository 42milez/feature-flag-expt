#!/usr/bin/env bash
set -euo pipefail

kubectl -n feature-flag-platform rollout status statefulset/feature-flag-postgres
kubectl -n feature-flag-platform rollout status deployment/feature-flag-platform
