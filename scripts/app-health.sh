#!/usr/bin/env bash
set -euo pipefail

readonly BASE_URL="${BASE_URL:-http://localhost:8080}"
readonly APP_HEALTH_ATTEMPTS="${APP_HEALTH_ATTEMPTS:-30}"
readonly APP_HEALTH_RETRY_DELAY_SECONDS="${APP_HEALTH_RETRY_DELAY_SECONDS:-2}"

fetch_health() {
  local url="$1"
  local attempt

  for attempt in $(seq 1 "${APP_HEALTH_ATTEMPTS}"); do
    if curl --fail --silent --show-error "${url}"; then
      return 0
    fi

    if [[ "${attempt}" -lt "${APP_HEALTH_ATTEMPTS}" ]]; then
      sleep "${APP_HEALTH_RETRY_DELAY_SECONDS}"
    fi
  done

  return 1
}

print_health() {
  local name="$1"
  local path="$2"
  local url="${BASE_URL}${path}"
  local response

  printf '==> %s\n' "${name}"
  printf 'GET %s\n' "${url}"

  response="$(fetch_health "${url}")"
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
