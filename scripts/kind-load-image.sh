#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

if [[ "${SKIP_BOOT_JAR:-false}" != "true" ]]; then
  ./gradlew :service:bootJar
fi

docker build -t feature-flag-platform:local ./service
kind load docker-image feature-flag-platform:local --name feature-flag-platform
