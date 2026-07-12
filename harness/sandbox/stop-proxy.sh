#!/usr/bin/env bash
# Stops (removes) the egress proxy sidecar. Deliberately does NOT remove the network or the
# coursier cache volume — the network is cheap/idempotent to recreate on the next start, and
# the volume persisting across loop runs is the entire point of the AC6 cache speedup.
set -euo pipefail
SELF_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
source "$SELF_DIR/lib.sh"

docker rm -f "$PROXY_NAME" >/dev/null 2>&1 || true
echo "[sandbox] proxy $PROXY_NAME stopped" >&2
