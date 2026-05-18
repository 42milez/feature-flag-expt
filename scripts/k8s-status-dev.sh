#!/usr/bin/env bash
set -euo pipefail

kubectl -n feature-flag-platform get pods -o wide
