#!/usr/bin/env bash
# Central helper library for manual curl tests against the s3kit demo (or any similar HTTP API).
# Usage: source lib.sh; then use the functions below. Call `summary` at the end (exit code 0 = all passed).

BASE_URL="${BASE_URL:-http://localhost:8080}"
AUTH_USER="${AUTH_USER:-user-a}"
AUTH_PASSWORD="${AUTH_PASSWORD:-change-me}"

LIB_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RESOURCES="${RESOURCES:-$LIB_DIR/resources}"
WORK_DIR="$(mktemp -d)"
trap 'rm -rf "$WORK_DIR"' EXIT

PASSED=0
FAILED=0
LAST_STATUS=""
LAST_BODY="$WORK_DIR/last-body"
LAST_HEADERS="$WORK_DIR/last-headers"

if [[ -t 1 ]]; then GREEN=$'\e[32m'; RED=$'\e[31m'; RESET=$'\e[0m'; else GREEN=""; RED=""; RESET=""; fi

# request METHOD PATH [extra curl args...] - performs the call with basic auth, sets LAST_STATUS, LAST_BODY, LAST_HEADERS.
# Use AUTH_USER=- to send no credentials.
request() {
  local method="$1" path="$2"; shift 2
  local auth=(-u "$AUTH_USER:$AUTH_PASSWORD")
  [[ "$AUTH_USER" == "-" ]] && auth=()
  LAST_STATUS=$(curl -sS -o "$LAST_BODY" -D "$LAST_HEADERS" -w '%{http_code}' -X "$method" "${auth[@]}" "$@" "$BASE_URL$path") || LAST_STATUS="000"
}

# header NAME - value of a response header of the last request (case-insensitive)
header() {
  grep -i "^$1:" "$LAST_HEADERS" | tail -1 | sed -e "s/^[^:]*:[[:space:]]*//" -e 's/\r$//'
}

pass() { PASSED=$((PASSED + 1)); echo "${GREEN}PASS${RESET} $1"; }
fail() { FAILED=$((FAILED + 1)); echo "${RED}FAIL${RESET} $1${2:+ - $2}"; }

# check_eq DESCRIPTION EXPECTED ACTUAL
check_eq() {
  if [[ "$2" == "$3" ]]; then pass "$1"; else fail "$1" "expected '$2' but was '$3'"; fi
}

# check_contains DESCRIPTION NEEDLE HAYSTACK
check_contains() {
  if [[ "$3" == *"$2"* ]]; then pass "$1"; else fail "$1" "'$3' does not contain '$2'"; fi
}

check_status() { check_eq "$1" "$2" "$LAST_STATUS"; }

# check_same_file DESCRIPTION FILE_A FILE_B - compares content
check_same_file() {
  if cmp -s "$2" "$3"; then pass "$1"; else fail "$1" "files differ: $2 $3"; fi
}

# make_file PATH SIZE_IN_BYTES - creates a file with random content
make_file() {
  head -c "$2" /dev/urandom > "$1"
}

# wait_for_server [SECONDS] - waits until the server answers on /actuator/health
wait_for_server() {
  local i
  for ((i = 0; i < ${1:-30}; i++)); do
    curl -sf -o /dev/null "$BASE_URL/actuator/health" && return 0
    sleep 1
  done
  echo "Server at $BASE_URL is not reachable" >&2
  return 1
}

summary() {
  echo "----"
  echo "passed: $PASSED, failed: $FAILED"
  [[ "$FAILED" -eq 0 ]]
}
