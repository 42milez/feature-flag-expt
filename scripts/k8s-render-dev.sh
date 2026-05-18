#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

kubectl kustomize deploy/k8s/overlays/dev
