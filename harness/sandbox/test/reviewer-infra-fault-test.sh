#!/usr/bin/env bash
# issue #37: run-reviewer.sh maps every infra fault to exit 124 (the infra-fault code loop.sh's
# dispatch_review turns into an rc-50 exit that spends NO repair budget and opens no PR).
# Deterministic and daemon-free: a missing API key fails before any docker call, and a bogus
# DOCKER_HOST makes the `docker info` probe fail. This proves the "no orphan, exit infra-fault"
# contract's exit code without a real container (the timeout kill itself is a trap in
# run-reviewer.sh over the same detached-container + docker-wait pattern the other roles ship).
set -euo pipefail
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
source "$SCRIPT_DIR/lib.sh"

fail=0
prompt="review this: VERDICT: APPROVE"

# 1. No ANTHROPIC_API_KEY -> infra fault before any docker call.
rc=0
( unset ANTHROPIC_API_KEY; "$SCRIPT_DIR/run-reviewer.sh" "$prompt" ) >/dev/null 2>&1 || rc=$?
if [[ "$rc" == "124" ]]; then
  echo "  ok   missing ANTHROPIC_API_KEY -> rc 124 (infra fault, no repair budget)"
else
  echo "  FAIL expected rc 124 with no API key, got: $rc"; fail=1
fi

# 2. API key present but Docker unreachable -> infra fault at the docker info probe.
rc=0
ANTHROPIC_API_KEY=dummy DOCKER_HOST="tcp://127.0.0.1:1" \
  "$SCRIPT_DIR/run-reviewer.sh" "$prompt" >/dev/null 2>&1 || rc=$?
if [[ "$rc" == "124" ]]; then
  echo "  ok   unreachable Docker at dispatch time -> rc 124 (infra fault, no repair budget)"
else
  echo "  FAIL expected rc 124 on unreachable Docker, got: $rc"; fail=1
fi

exit "$fail"
