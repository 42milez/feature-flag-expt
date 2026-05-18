#!/usr/bin/env bash
set -euo pipefail

kubectl -n feature-flag-platform port-forward service/feature-flag-platform 8080:8080
