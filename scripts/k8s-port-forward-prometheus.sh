#!/usr/bin/env bash
set -euo pipefail

kubectl -n feature-flag-platform port-forward service/prometheus 9090:9090
