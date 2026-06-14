#!/usr/bin/env bash
set -euo pipefail

readonly BASE_URL="${BASE_URL:-http://localhost:8080}"

print_health() {
  local name="$1"
  local path="$2"
  local url="${BASE_URL}${path}"
  local response

  printf '==> %s\n' "${name}"
  printf 'GET %s\n' "${url}"

  response="$(curl --fail --silent --show-error "${url}")"
  if command -v jq >/dev/null 2>&1; then
    printf '%s\n' "${response}" | jq .
  else
    printf '%s\n' "${response}"
  fi
  printf '\n'
}

print_health "Overall health" "/actuator/health"
print_health "Liveness" "/actuator/health/liveness"
print_health "Readiness" "/actuator/health/readiness"
