#!/usr/bin/env bash
# Idempotent build of both sandbox images (gate + proxy). Always runs `docker build`, which
# is a fast no-op when the layer cache is unchanged — see harness/sandbox/lib.sh for tags.
set -euo pipefail
SELF_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
source "$SELF_DIR/lib.sh"

echo "[sandbox] building $IMAGE ..." >&2
docker build -t "$IMAGE" -f "$SANDBOX_DIR/Dockerfile" "$SANDBOX_DIR"

echo "[sandbox] building $PROXY_IMAGE ..." >&2
docker build -t "$PROXY_IMAGE" -f "$SANDBOX_DIR/proxy/Dockerfile" "$SANDBOX_DIR/proxy"

echo "[sandbox] images built: $IMAGE, $PROXY_IMAGE" >&2
