#!/usr/bin/env bash
# AC (issue #37): the egress fence is verified from EVERY container role. For each of the four
# roles — worker, fixer, gate, reviewer — a request to an allowlisted host succeeds and a request
# to a non-allowlisted host is refused by the proxy. Supersedes the single-shot
# proxy-allowlist-test.sh: same refusal signature, now asserted once per role in the exact network
# posture that role runs in.
#
# The two egress mechanisms in play (all roles join fes-sandbox-net only, so the proxy is the sole
# reachable host either way):
#   - env    : HTTP(S)_PROXY set in the container env — how run-agent.sh (worker/fixer) and
#              run-reviewer.sh (reviewer) point `claude`'s fetch at the proxy. `curl` reads the
#              same env, so a bare `curl` faithfully exercises that path.
#   - curl-x : proxy given explicitly via `curl -x` — the faithful curl analog of run-fast-gate.sh,
#              whose sbt/coursier JVM reaches the proxy via -Dhttp(s).proxyHost props (a JVM prop
#              curl cannot read, so -x stands in for it).
#
# Empirically verified refusal signature (tinyproxy 1.11.2, alpine:3.20, FilterDefaultDeny Yes): a
# filtered CONNECT gets "HTTP/1.1 403 Filtered", which curl reports as a failed tunnel — curl exit
# code 7, http_code 000. Requires the proxy sidecar already running (start-proxy.sh) and $NETWORK.
set -euo pipefail
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
source "$SCRIPT_DIR/lib.sh"

ALLOWED_HOST="repo1.maven.org"    # on sandbox/proxy/allowlist
BLOCKED_HOST="example.com"        # deliberately not on the allowlist
fail=0

[[ "$(docker inspect -f '{{.State.Running}}' "$PROXY_NAME" 2>/dev/null || true)" == "true" ]] \
  || { echo "  FAIL proxy $PROXY_NAME is not running -- run harness/sandbox/start-proxy.sh first" >&2; exit 1; }

echo "== egress policy test: every container role reaches allowlisted hosts, is refused elsewhere ==" >&2

proxy_url="http://$PROXY_NAME:$PROXY_PORT"

# run_curl MECHANISM HOST -> prints "<http_code> <curl_exit_code>". Uses the sandbox image itself
# (it ships curl), so egress is exercised from the exact image every role runs in.
run_curl() {
  local mech="$1" host="$2" code rc=0
  if [[ "$mech" == "env" ]]; then
    code="$(docker run --rm --network "$NETWORK" \
      -e HTTP_PROXY="$proxy_url" -e HTTPS_PROXY="$proxy_url" \
      -e http_proxy="$proxy_url" -e https_proxy="$proxy_url" \
      --entrypoint curl "$IMAGE" \
      -s -o /dev/null -w '%{http_code}' --max-time 15 "https://$host/" 2>/dev/null)" || rc=$?
  else # curl-x
    code="$(docker run --rm --network "$NETWORK" --entrypoint curl "$IMAGE" \
      -x "$proxy_url" -s -o /dev/null -w '%{http_code}' --max-time 15 "https://$host/" 2>/dev/null)" || rc=$?
  fi
  printf '%s %s' "${code:-000}" "$rc"
}

# check_role ROLE MECHANISM
check_role() {
  local role="$1" mech="$2" allowed_code allowed_rc blocked_code blocked_rc
  read -r allowed_code allowed_rc <<<"$(run_curl "$mech" "$ALLOWED_HOST")"
  read -r blocked_code blocked_rc <<<"$(run_curl "$mech" "$BLOCKED_HOST")"

  if [[ "$allowed_rc" == "0" && "$allowed_code" == 2* ]]; then
    echo "  ok   $role reaches allowlisted $ALLOWED_HOST (http=$allowed_code, via $mech)"
  else
    echo "  FAIL $role could not reach $ALLOWED_HOST: http=$allowed_code curl_rc=$allowed_rc (via $mech)"; fail=1
  fi

  if [[ "$blocked_rc" != "0" ]]; then
    echo "  ok   $role refused for non-allowlisted $BLOCKED_HOST (curl_rc=$blocked_rc, http=$blocked_code, via $mech)"
  else
    echo "  FAIL $role reached non-allowlisted $BLOCKED_HOST (http=$blocked_code curl_rc=$blocked_rc, via $mech)"; fail=1
  fi
}

check_role worker   env      # run-agent.sh: HTTP(S)_PROXY env
check_role fixer    env      # run-agent.sh (same script/config as worker)
check_role reviewer env      # run-reviewer.sh: HTTP(S)_PROXY env
check_role gate     curl-x   # run-fast-gate.sh: JVM -Dhttp(s).proxyHost props (curl -x stands in)

exit "$fail"
