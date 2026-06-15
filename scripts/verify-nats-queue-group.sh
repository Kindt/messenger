#!/usr/bin/env bash
# Verify message-pipeline replicas subscribe to the same NATS queue group (spec 006 T204).
set -euo pipefail
ROOT="${ROOT:-$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)}"
EXPECTED_GROUP="${PIPELINE_QUEUE_GROUP:-pipeline-workers}"
FOUND=0
for name in message-pipeline message-pipeline-2; do
  cid=$(docker ps --filter "name=${name}" --format '{{.Names}}' | head -1 || true)
  if [[ -z "$cid" ]]; then
    echo "[skip] container $name not running"
    continue
  fi
  if docker logs "$cid" 2>&1 | tail -n 200 | grep -q "$EXPECTED_GROUP"; then
    echo "[OK] $cid logs mention queue group $EXPECTED_GROUP"
    FOUND=$((FOUND + 1))
  else
    echo "[FAIL] $cid: queue group $EXPECTED_GROUP not found in recent logs" >&2
    exit 1
  fi
done
if [[ "$FOUND" -lt 1 ]]; then
  echo "[FAIL] no running message-pipeline containers" >&2
  exit 1
fi
echo "[OK] NATS queue group verification ($FOUND replica(s))"
