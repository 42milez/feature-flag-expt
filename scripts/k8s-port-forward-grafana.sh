#!/usr/bin/env bash
set -euo pipefail

kubectl -n feature-flag-platform port-forward service/grafana 3000:3000
