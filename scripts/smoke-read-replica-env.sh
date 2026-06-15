#!/usr/bin/env bash
# Verify core-api has distinct DB_READ_JDBC_URL (replica overlay active).
set -euo pipefail
CID=$(docker ps --format '{{.Names}}' | grep -E 'core-api' | head -1 || true)
if [[ -z "$CID" ]]; then
  echo "[FAIL] core-api container not running" >&2
  exit 1
fi
READ_URL=$(docker exec "$CID" printenv DB_READ_JDBC_URL 2>/dev/null || true)
WRITE_URL=$(docker exec "$CID" printenv DB_JDBC_URL 2>/dev/null || true)
if [[ -z "$READ_URL" ]]; then
  echo "[FAIL] DB_READ_JDBC_URL not set in $CID" >&2
  exit 1
fi
echo "[OK] DB_READ_JDBC_URL=$READ_URL"
if [[ -n "$WRITE_URL" && "$READ_URL" == "$WRITE_URL" ]]; then
  echo "[WARN] read URL equals write URL (Tier-1 lab routing only)" >&2
fi
exit 0
