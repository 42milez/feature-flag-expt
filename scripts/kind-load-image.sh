#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

docker build -f service/Dockerfile -t feature-flag-platform:local .
kind load docker-image feature-flag-platform:local --name feature-flag-platform
