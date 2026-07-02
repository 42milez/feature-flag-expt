#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
BASE_URL="${BASE_URL%/}"
readonly BASE_URL
readonly FLAG_PREFIX="${FLAG_PREFIX:-dashboard-demo}"
readonly FLAG_COUNT="${FLAG_COUNT:-8}"
readonly EVALUATIONS_PER_FLAG="${EVALUATIONS_PER_FLAG:-250}"
readonly OPERATOR_USER="${OPERATOR_USER:-featureflags-operator}"
readonly OPERATOR_PASSWORD="${OPERATOR_PASSWORD:-featureflags-operator}"
readonly READER_USER="${READER_USER:-featureflags-reader}"
readonly READER_PASSWORD="${READER_PASSWORD:-featureflags-reader}"
readonly ALLOW_REMOTE="${ALLOW_REMOTE:-false}"

readonly TENANT_COUNT=40
readonly USER_COUNT=200
readonly MAX_FLAG_COUNT=50
readonly READINESS_ATTEMPTS=30
readonly READINESS_RETRY_DELAY_SECONDS=2
readonly POST_UPDATE_EVALUATIONS=40
readonly MAX_FAILURE_EXAMPLES=5
readonly GRAFANA_URL="http://localhost:3000/d/feature-flag-overview/feature-flag-overview?orgId=1&from=now-30m&to=now&timezone=browser&var-DS_PROMETHEUS=prometheus&refresh=30s"

OPERATOR_CURL_CONFIG=""
READER_CURL_CONFIG=""
HTTP_STATUS=""
HTTP_BODY=""

AVAILABLE_FLAGS=()
AVAILABLE_FLAG_NUMBERS=()
AVAILABLE_ROLLOUTS=()
AVAILABLE_KILL_SWITCHES=()
AVAILABLE_FIRST_ALLOWLIST_TENANTS=()

FLAGS_CREATED=0
FLAGS_REUSED=0
FLAGS_POLICY_SKIPPED=0
FLAGS_FAILED=0

EVALUATIONS_ATTEMPTED=0
EVALUATIONS_SUCCEEDED=0
EVALUATIONS_HTTP_FAILED=0
EVALUATIONS_TRANSPORT_FAILED=0
EVALUATION_FAILURE_EXAMPLES=""
EVALUATION_FAILURE_EXAMPLE_COUNT=0

UPDATE_ATTEMPTED=0
UPDATE_SUCCEEDED=0
UPDATE_POLICY_SKIPPED=0
STAGED_ROLLOUT_KEY=""
STAGED_ROLLOUT_RESULT="not attempted"

KILL_SWITCH_ATTEMPTED=0
KILL_SWITCH_SUCCEEDED=0
KILL_SWITCH_POLICY_SKIPPED=0
KILL_SWITCH_KEY=""
KILL_SWITCH_RESULT="not attempted"

cleanup() {
  rm -f "${OPERATOR_CURL_CONFIG:-}" "${READER_CURL_CONFIG:-}"
}
trap cleanup EXIT

die() {
  printf 'ERROR: %s\n' "$1" >&2
  exit 1
}

require_command() {
  local name="$1"

  if ! command -v "${name}" >/dev/null 2>&1; then
    die "Required command '${name}' was not found."
  fi
}

require_curl_fail_with_body() {
  if ! curl --help all 2>/dev/null | grep -q -- '--fail-with-body'; then
    die "This script requires a curl version that supports --fail-with-body."
  fi
}

is_unsigned_integer() {
  [[ "$1" =~ ^[0-9]+$ ]]
}

validate_configuration() {
  if ! is_unsigned_integer "${FLAG_COUNT}" || [[ "${FLAG_COUNT}" -lt 1 ]]; then
    die "FLAG_COUNT must be a positive integer."
  fi
  if [[ "${FLAG_COUNT}" -gt "${MAX_FLAG_COUNT}" ]]; then
    die "FLAG_COUNT must be ${MAX_FLAG_COUNT} or lower because flag.key is a metric label."
  fi
  if ! is_unsigned_integer "${EVALUATIONS_PER_FLAG}"; then
    die "EVALUATIONS_PER_FLAG must be a non-negative integer."
  fi
  if ! [[ "${FLAG_PREFIX}" =~ ^[A-Za-z0-9._-]+$ ]]; then
    die "FLAG_PREFIX may contain only letters, numbers, dots, underscores, and hyphens."
  fi
  if [[ "${#FLAG_PREFIX}" -gt 190 ]]; then
    die "FLAG_PREFIX must be 190 characters or shorter so generated flag keys fit API limits."
  fi
}

extract_url_host() {
  local url="$1"
  local without_scheme
  local authority
  local host

  if [[ "${url}" == *"://"* ]]; then
    without_scheme="${url#*://}"
  else
    without_scheme="${url}"
  fi

  authority="${without_scheme%%/*}"
  authority="${authority##*@}"

  if [[ "${authority}" == \[* ]]; then
    host="${authority%%]*}]"
  else
    host="${authority%%:*}"
  fi

  printf '%s\n' "${host}"
}

is_local_base_url() {
  local host

  host="$(extract_url_host "${BASE_URL}")"
  case "${host}" in
    localhost | 127.0.0.1 | "[::1]")
      return 0
      ;;
    *)
      return 1
      ;;
  esac
}

validate_target_safety() {
  local host

  host="$(extract_url_host "${BASE_URL}")"
  if [[ -z "${host}" ]]; then
    die "BASE_URL must include a host."
  fi

  if is_local_base_url; then
    printf 'Using local target %s.\n' "${BASE_URL}"
    return
  fi

  if [[ "${ALLOW_REMOTE}" == "true" ]]; then
    printf 'ALLOW_REMOTE=true; using non-local target %s.\n' "${BASE_URL}"
    printf 'This script creates flags and patches rollout and kill-switch state.\n'
    return
  fi

  die "BASE_URL host '${host}' is not local. This script creates flags and patches kill switches; set ALLOW_REMOTE=true to continue intentionally."
}

curl_config_escape() {
  local value="$1"

  if [[ "${value}" == *$'\n'* ]]; then
    die "Credentials must not contain newlines."
  fi

  value="${value//\\/\\\\}"
  value="${value//\"/\\\"}"
  printf '%s\n' "${value}"
}

create_curl_config() {
  local username="$1"
  local password="$2"
  local file
  local escaped_user

  file="$(mktemp "${TMPDIR:-/tmp}/feature-flag-traffic-curl.XXXXXX")"
  chmod 600 "${file}"
  escaped_user="$(curl_config_escape "${username}:${password}")"
  {
    printf 'basic\n'
    printf 'user = "%s"\n' "${escaped_user}"
  } >"${file}"
  printf '%s\n' "${file}"
}

check_readiness() {
  local attempt

  printf 'Checking application readiness at %s/actuator/health/readiness ...\n' "${BASE_URL}"
  for attempt in $(seq 1 "${READINESS_ATTEMPTS}"); do
    if curl --fail-with-body --silent --show-error \
      "${BASE_URL}/actuator/health/readiness" >/dev/null; then
      printf 'Application readiness check passed.\n'
      return
    fi

    if [[ "${attempt}" -lt "${READINESS_ATTEMPTS}" ]]; then
      sleep "${READINESS_RETRY_DELAY_SECONDS}"
    fi
  done

  die "Application readiness did not pass after ${READINESS_ATTEMPTS} attempts."
}

send_request() {
  local config_file="$1"
  local method="$2"
  local path="$3"
  local body="${4:-}"
  local body_file
  local curl_exit
  local -a curl_args

  HTTP_STATUS=""
  HTTP_BODY=""
  body_file="$(mktemp "${TMPDIR:-/tmp}/feature-flag-traffic-response.XXXXXX")"
  curl_args=(
    --config "${config_file}"
    --silent
    --show-error
    --output "${body_file}"
    --write-out '%{http_code}'
    --request "${method}"
  )
  if [[ -n "${body}" ]]; then
    curl_args+=(--header 'Content-Type: application/json' --data "${body}")
  fi
  curl_args+=("${BASE_URL}${path}")

  if HTTP_STATUS="$(curl "${curl_args[@]}")"; then
    HTTP_BODY="$(cat "${body_file}")"
    rm -f "${body_file}"
    return 0
  else
    curl_exit=$?
  fi

  HTTP_STATUS="000"
  HTTP_BODY="curl exited with status ${curl_exit}"
  rm -f "${body_file}"
  return 1
}

fail_if_auth_failure() {
  local status="$1"
  local operation="$2"

  if [[ "${status}" == "401" || "${status}" == "403" ]]; then
    die "${operation} returned HTTP ${status}. Check the configured usernames, passwords, and roles."
  fi
}

sanitize_body() {
  local body="$1"
  local compact

  if [[ -z "${body}" ]]; then
    printf '<empty>'
    return
  fi

  if compact="$(printf '%s' "${body}" | jq -c . 2>/dev/null)"; then
    :
  else
    compact="$(printf '%s' "${body}" | tr '\n\r' '  ')"
  fi

  printf '%s' "${compact:0:180}"
}

record_evaluation_failure_example() {
  local flag_key="$1"
  local phase="$2"
  local status="$3"
  local body="$4"
  local sanitized

  if [[ "${EVALUATION_FAILURE_EXAMPLE_COUNT}" -ge "${MAX_FAILURE_EXAMPLES}" ]]; then
    return
  fi

  sanitized="$(sanitize_body "${body}")"
  EVALUATION_FAILURE_EXAMPLES="${EVALUATION_FAILURE_EXAMPLES}  - ${phase} ${flag_key}: HTTP ${status}: ${sanitized}"$'\n'
  EVALUATION_FAILURE_EXAMPLE_COUNT=$((EVALUATION_FAILURE_EXAMPLE_COUNT + 1))
}

flag_key_for_number() {
  local flag_number="$1"
  local suffix

  printf -v suffix '%03d' "${flag_number}"
  printf '%s-%s\n' "${FLAG_PREFIX}" "${suffix}"
}

rollout_for_flag_number() {
  local flag_number="$1"

  case $(((flag_number - 1) % 5)) in
    0)
      printf '0\n'
      ;;
    1)
      printf '10\n'
      ;;
    2)
      printf '25\n'
      ;;
    3)
      printf '50\n'
      ;;
    *)
      printf '75\n'
      ;;
  esac
}

tenant_id_for_number() {
  local tenant_number="$1"
  local suffix

  printf -v suffix '%03d' "${tenant_number}"
  printf 'tenant-%s\n' "${suffix}"
}

user_id_for_number() {
  local user_number="$1"
  local suffix

  printf -v suffix '%06d' "${user_number}"
  printf 'user-%s\n' "${suffix}"
}

tenant_allowlist_json_for_flag() {
  local flag_number="$1"
  local count
  local start
  local offset
  local tenant_number
  local -a tenants

  tenants=()
  count=$((3 + ((flag_number - 1) % 3)))
  start=$(((((flag_number - 1) * 5) % TENANT_COUNT) + 1))
  for offset in $(seq 0 $((count - 1))); do
    tenant_number=$((((start + offset - 1) % TENANT_COUNT) + 1))
    tenants+=("$(tenant_id_for_number "${tenant_number}")")
  done

  jq -n -c '$ARGS.positional' --args "${tenants[@]}"
}

create_flag_body() {
  local flag_key="$1"
  local flag_number="$2"
  local rollout
  local allowlist_json

  rollout="$(rollout_for_flag_number "${flag_number}")"
  allowlist_json="$(tenant_allowlist_json_for_flag "${flag_number}")"
  jq -n -c \
    --arg flagKey "${flag_key}" \
    --argjson tenantAllowlist "${allowlist_json}" \
    --argjson rolloutPercentage "${rollout}" \
    '{
      flagKey: $flagKey,
      status: "ENABLED",
      targetEnvironments: ["production", "staging"],
      killSwitchActive: false,
      tenantAllowlist: $tenantAllowlist,
      rolloutPercentage: $rolloutPercentage
    }'
}

fetch_flag_state() {
  local flag_key="$1"

  if ! send_request "${READER_CURL_CONFIG}" GET "/api/flags/${flag_key}"; then
    die "Transport failure while fetching reused flag '${flag_key}'."
  fi
  fail_if_auth_failure "${HTTP_STATUS}" "Fetch flag ${flag_key}"
  if [[ "${HTTP_STATUS}" != "200" ]]; then
    die "Expected HTTP 200 while fetching '${flag_key}', got HTTP ${HTTP_STATUS}: $(sanitize_body "${HTTP_BODY}")"
  fi
}

add_available_flag() {
  local flag_key="$1"
  local flag_number="$2"
  local state_json="$3"
  local rollout
  local kill_switch
  local first_allowlist_tenant

  rollout="$(printf '%s' "${state_json}" | jq -r '.rolloutPercentage')"
  kill_switch="$(printf '%s' "${state_json}" | jq -r '.killSwitchActive')"
  first_allowlist_tenant="$(printf '%s' "${state_json}" | jq -r '.tenantAllowlist[0] // empty')"
  if [[ -z "${first_allowlist_tenant}" ]]; then
    first_allowlist_tenant="$(tenant_id_for_number 1)"
  fi

  AVAILABLE_FLAGS+=("${flag_key}")
  AVAILABLE_FLAG_NUMBERS+=("${flag_number}")
  AVAILABLE_ROLLOUTS+=("${rollout}")
  AVAILABLE_KILL_SWITCHES+=("${kill_switch}")
  AVAILABLE_FIRST_ALLOWLIST_TENANTS+=("${first_allowlist_tenant}")
}

create_or_reuse_flags() {
  local flag_number
  local flag_key
  local body
  local state_json

  printf 'Creating or reusing %s deterministic flags with prefix "%s" ...\n' "${FLAG_COUNT}" "${FLAG_PREFIX}"
  for flag_number in $(seq 1 "${FLAG_COUNT}"); do
    flag_key="$(flag_key_for_number "${flag_number}")"
    body="$(create_flag_body "${flag_key}" "${flag_number}")"

    if ! send_request "${OPERATOR_CURL_CONFIG}" POST "/api/flags" "${body}"; then
      FLAGS_FAILED=$((FLAGS_FAILED + 1))
      die "Transport failure while creating '${flag_key}'."
    fi

    fail_if_auth_failure "${HTTP_STATUS}" "Create flag ${flag_key}"
    case "${HTTP_STATUS}" in
      201)
        FLAGS_CREATED=$((FLAGS_CREATED + 1))
        state_json="${HTTP_BODY}"
        add_available_flag "${flag_key}" "${flag_number}" "${state_json}"
        ;;
      409)
        FLAGS_REUSED=$((FLAGS_REUSED + 1))
        fetch_flag_state "${flag_key}"
        state_json="${HTTP_BODY}"
        add_available_flag "${flag_key}" "${flag_number}" "${state_json}"
        ;;
      422)
        FLAGS_POLICY_SKIPPED=$((FLAGS_POLICY_SKIPPED + 1))
        printf 'Skipped %s because policy validation rejected it: %s\n' \
          "${flag_key}" "$(sanitize_body "${HTTP_BODY}")"
        ;;
      *)
        FLAGS_FAILED=$((FLAGS_FAILED + 1))
        die "Unexpected create response for '${flag_key}': HTTP ${HTTP_STATUS}: $(sanitize_body "${HTTP_BODY}")"
        ;;
    esac
  done

  if [[ "${#AVAILABLE_FLAGS[@]}" -eq 0 ]]; then
    die "No generated flags are available for evaluation."
  fi
}

evaluation_body() {
  local flag_key="$1"
  local environment="$2"
  local tenant_id="$3"
  local user_id="$4"

  jq -n -c \
    --arg flagKey "${flag_key}" \
    --arg environment "${environment}" \
    --arg tenantId "${tenant_id}" \
    --arg userId "${user_id}" \
    '{
      flagKey: $flagKey,
      environment: $environment
    }
    + (if $tenantId == "" then {} else {tenantId: $tenantId} end)
    + (if $userId == "" then {} else {userId: $userId} end)'
}

send_single_evaluation() {
  local available_index="$1"
  local request_number="$2"
  local phase="$3"
  local flag_key
  local flag_number
  local environment
  local tenant_id
  local user_id
  local tenant_number
  local user_number
  local body

  flag_key="${AVAILABLE_FLAGS[available_index]}"
  flag_number="${AVAILABLE_FLAG_NUMBERS[available_index]}"

  if [[ $(((request_number + flag_number) % 2)) -eq 0 ]]; then
    environment="production"
  else
    environment="staging"
  fi

  tenant_id=""
  user_id=""
  if [[ $((request_number % 5)) -eq 0 ]]; then
    tenant_id="${AVAILABLE_FIRST_ALLOWLIST_TENANTS[available_index]}"
    user_number=$((((request_number + flag_number) % USER_COUNT) + 1))
    user_id="$(user_id_for_number "${user_number}")"
  elif [[ $((request_number % 6)) -eq 0 ]]; then
    tenant_number=$(((((flag_number * 7) + request_number) % TENANT_COUNT) + 1))
    tenant_id="$(tenant_id_for_number "${tenant_number}")"
  else
    user_number=$(((((flag_number * 31) + request_number) % USER_COUNT) + 1))
    user_id="$(user_id_for_number "${user_number}")"
  fi

  body="$(evaluation_body "${flag_key}" "${environment}" "${tenant_id}" "${user_id}")"
  EVALUATIONS_ATTEMPTED=$((EVALUATIONS_ATTEMPTED + 1))

  if ! send_request "${READER_CURL_CONFIG}" POST "/api/evaluate" "${body}"; then
    EVALUATIONS_TRANSPORT_FAILED=$((EVALUATIONS_TRANSPORT_FAILED + 1))
    record_evaluation_failure_example "${flag_key}" "${phase}" "000" "${HTTP_BODY}"
    return
  fi

  fail_if_auth_failure "${HTTP_STATUS}" "Evaluate flag ${flag_key}"
  if [[ "${HTTP_STATUS}" == "200" ]]; then
    EVALUATIONS_SUCCEEDED=$((EVALUATIONS_SUCCEEDED + 1))
    return
  fi

  EVALUATIONS_HTTP_FAILED=$((EVALUATIONS_HTTP_FAILED + 1))
  record_evaluation_failure_example "${flag_key}" "${phase}" "${HTTP_STATUS}" "${HTTP_BODY}"
}

send_evaluation_burst() {
  local available_index="$1"
  local count="$2"
  local phase="$3"
  local request_number

  if [[ "${count}" -eq 0 ]]; then
    return
  fi

  for request_number in $(seq 1 "${count}"); do
    send_single_evaluation "${available_index}" "${request_number}" "${phase}"
  done
}

generate_evaluations() {
  local available_index
  local available_count

  available_count="${#AVAILABLE_FLAGS[@]}"
  printf 'Generating %s evaluations for each of %s available flags ...\n' \
    "${EVALUATIONS_PER_FLAG}" "${available_count}"
  for available_index in $(seq 0 $((available_count - 1))); do
    send_evaluation_burst "${available_index}" "${EVALUATIONS_PER_FLAG}" "initial"
  done
}

patch_flag() {
  local flag_key="$1"
  local body="$2"
  local operation="$3"

  if ! send_request "${OPERATOR_CURL_CONFIG}" PATCH "/api/flags/${flag_key}" "${body}"; then
    die "Transport failure while patching '${flag_key}' for ${operation}."
  fi
  fail_if_auth_failure "${HTTP_STATUS}" "${operation} patch for ${flag_key}"
}

find_staged_rollout_index() {
  local available_index
  local available_count

  available_count="${#AVAILABLE_FLAGS[@]}"
  for available_index in $(seq 0 $((available_count - 1))); do
    if [[ "${AVAILABLE_ROLLOUTS[available_index]}" == "75" &&
      "${AVAILABLE_KILL_SWITCHES[available_index]}" != "true" ]]; then
      printf '%s\n' "${available_index}"
      return
    fi
  done

  printf '%s\n' "-1"
}

trigger_staged_rollout_update() {
  local available_index
  local flag_key
  local body

  available_index="$(find_staged_rollout_index)"
  if [[ "${available_index}" == "-1" ]]; then
    STAGED_ROLLOUT_RESULT="skipped; no available non-kill-switched flag currently has rolloutPercentage=75"
    return
  fi

  flag_key="${AVAILABLE_FLAGS[available_index]}"
  STAGED_ROLLOUT_KEY="${flag_key}"
  body="$(jq -n -c --argjson rolloutPercentage 100 '{rolloutPercentage: $rolloutPercentage}')"

  UPDATE_ATTEMPTED=$((UPDATE_ATTEMPTED + 1))
  patch_flag "${flag_key}" "${body}" "staged rollout"
  case "${HTTP_STATUS}" in
    200)
      UPDATE_SUCCEEDED=$((UPDATE_SUCCEEDED + 1))
      AVAILABLE_ROLLOUTS[available_index]="100"
      STAGED_ROLLOUT_RESULT="patched ${flag_key} from 75 to 100"
      printf 'Staged rollout update succeeded for %s; generating post-update evaluations ...\n' "${flag_key}"
      send_evaluation_burst "${available_index}" "${POST_UPDATE_EVALUATIONS}" "post-rollout"
      ;;
    422)
      UPDATE_POLICY_SKIPPED=$((UPDATE_POLICY_SKIPPED + 1))
      STAGED_ROLLOUT_RESULT="skipped by policy for ${flag_key}: $(sanitize_body "${HTTP_BODY}")"
      ;;
    *)
      die "Unexpected staged rollout patch response for '${flag_key}': HTTP ${HTTP_STATUS}: $(sanitize_body "${HTTP_BODY}")"
      ;;
  esac
}

find_kill_switch_index() {
  local available_index
  local available_count

  available_count="${#AVAILABLE_FLAGS[@]}"
  for available_index in $(seq 0 $((available_count - 1))); do
    if [[ "${AVAILABLE_KILL_SWITCHES[available_index]}" != "true" &&
      "${AVAILABLE_FLAGS[available_index]}" != "${STAGED_ROLLOUT_KEY}" ]]; then
      printf '%s\n' "${available_index}"
      return
    fi
  done

  for available_index in $(seq 0 $((available_count - 1))); do
    if [[ "${AVAILABLE_KILL_SWITCHES[available_index]}" != "true" ]]; then
      printf '%s\n' "${available_index}"
      return
    fi
  done

  printf '%s\n' "-1"
}

trigger_kill_switch_update() {
  local available_index
  local flag_key
  local body

  available_index="$(find_kill_switch_index)"
  if [[ "${available_index}" == "-1" ]]; then
    KILL_SWITCH_RESULT="skipped; all available generated flags are already kill-switched. Rerun with a new FLAG_PREFIX for a fresh event."
    return
  fi

  flag_key="${AVAILABLE_FLAGS[available_index]}"
  KILL_SWITCH_KEY="${flag_key}"
  body="$(jq -n -c --argjson killSwitchActive true '{killSwitchActive: $killSwitchActive}')"

  KILL_SWITCH_ATTEMPTED=$((KILL_SWITCH_ATTEMPTED + 1))
  patch_flag "${flag_key}" "${body}" "kill switch"
  case "${HTTP_STATUS}" in
    200)
      KILL_SWITCH_SUCCEEDED=$((KILL_SWITCH_SUCCEEDED + 1))
      AVAILABLE_KILL_SWITCHES[available_index]="true"
      KILL_SWITCH_RESULT="fresh false -> true transition produced for ${flag_key}"
      printf 'Kill-switch update succeeded for %s; generating post-update evaluations ...\n' "${flag_key}"
      send_evaluation_burst "${available_index}" "${POST_UPDATE_EVALUATIONS}" "post-kill-switch"
      ;;
    422)
      KILL_SWITCH_POLICY_SKIPPED=$((KILL_SWITCH_POLICY_SKIPPED + 1))
      KILL_SWITCH_RESULT="skipped by policy for ${flag_key}: $(sanitize_body "${HTTP_BODY}")"
      ;;
    *)
      die "Unexpected kill-switch patch response for '${flag_key}': HTTP ${HTTP_STATUS}: $(sanitize_body "${HTTP_BODY}")"
      ;;
  esac
}

print_summary() {
  printf '\n==> Observability traffic summary\n'
  printf 'Flags: created=%s reused=%s policy_skipped=%s failed=%s available=%s\n' \
    "${FLAGS_CREATED}" "${FLAGS_REUSED}" "${FLAGS_POLICY_SKIPPED}" "${FLAGS_FAILED}" "${#AVAILABLE_FLAGS[@]}"
  printf 'Evaluations: attempted=%s succeeded=%s http_failed=%s transport_failed=%s\n' \
    "${EVALUATIONS_ATTEMPTED}" "${EVALUATIONS_SUCCEEDED}" "${EVALUATIONS_HTTP_FAILED}" "${EVALUATIONS_TRANSPORT_FAILED}"
  if [[ -n "${EVALUATION_FAILURE_EXAMPLES}" ]]; then
    printf 'First evaluation failures:\n%s' "${EVALUATION_FAILURE_EXAMPLES}"
  fi
  printf 'Staged rollout update: attempted=%s succeeded=%s policy_skipped=%s result=%s\n' \
    "${UPDATE_ATTEMPTED}" "${UPDATE_SUCCEEDED}" "${UPDATE_POLICY_SKIPPED}" "${STAGED_ROLLOUT_RESULT}"
  printf 'Kill-switch update: attempted=%s succeeded=%s policy_skipped=%s result=%s\n' \
    "${KILL_SWITCH_ATTEMPTED}" "${KILL_SWITCH_SUCCEEDED}" "${KILL_SWITCH_POLICY_SKIPPED}" "${KILL_SWITCH_RESULT}"
  printf '\nGrafana dashboard:\n%s\n' "${GRAFANA_URL}"
  printf 'Prometheus may need one 15-second scrape interval before Grafana panels refresh.\n'
}

main() {
  require_command curl
  require_command jq
  require_curl_fail_with_body
  validate_configuration
  validate_target_safety
  check_readiness

  OPERATOR_CURL_CONFIG="$(create_curl_config "${OPERATOR_USER}" "${OPERATOR_PASSWORD}")"
  READER_CURL_CONFIG="$(create_curl_config "${READER_USER}" "${READER_PASSWORD}")"

  create_or_reuse_flags
  generate_evaluations
  trigger_staged_rollout_update
  trigger_kill_switch_update
  print_summary
}

main "$@"
