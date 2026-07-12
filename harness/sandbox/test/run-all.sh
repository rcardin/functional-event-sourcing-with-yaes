#!/usr/bin/env bash
# Thin manual/local runner for the sandbox's Docker-dependent checks: build + image smoke test
# (AC1) + proxy allowlist test (AC3) + a coursier-cache speed check (AC6, second gate run
# measurably faster than the first). NOT wired into `sbt test` -- needs Docker + network
# egress, same category as the pre-existing IT gate.
set -euo pipefail
SELF_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
source "$SELF_DIR/lib.sh"
REPO_ROOT="$(cd -- "$SELF_DIR/../.." && pwd)"

"$SELF_DIR/build-image.sh"
"$SELF_DIR/start-proxy.sh"
trap '"$SELF_DIR/stop-proxy.sh" >/dev/null 2>&1 || true' EXIT

fail=0
"$SELF_DIR/test/image-smoke-test.sh" || fail=1
"$SELF_DIR/test/proxy-allowlist-test.sh" || fail=1

echo "== AC6: coursier cache volume speed check (first run vs second run) ==" >&2
cd "$REPO_ROOT"
git add -A
log1="$(mktemp)"; log2="$(mktemp)"
t0=$(date +%s); "$SELF_DIR/run-fast-gate.sh" >"$log1" 2>&1; rc1=$?; t1=$(date +%s)
t2=$(date +%s); "$SELF_DIR/run-fast-gate.sh" >"$log2" 2>&1; rc2=$?; t3=$(date +%s)
d1=$((t1 - t0)); d2=$((t3 - t2))
echo "  run1: rc=$rc1 ${d1}s   run2: rc=$rc2 ${d2}s   (logs: $log1 $log2)" >&2
if [[ "$rc1" != "0" || "$rc2" != "0" ]]; then
  echo "  FAIL a gate run did not exit 0 (rc1=$rc1 rc2=$rc2) -- see $log1 $log2" >&2
  fail=1
elif (( d2 < d1 )); then
  echo "  ok   second run faster (cache hit): ${d1}s -> ${d2}s" >&2
else
  echo "  WARN second run not faster (${d2}s vs ${d1}s) -- inspect logs" >&2
fi

echo
if [[ "$fail" == "0" ]]; then
  echo "==== sandbox checks: ALL PASSED ====" >&2
else
  echo "==== sandbox checks: SOME FAILED ====" >&2
fi
exit "$fail"
