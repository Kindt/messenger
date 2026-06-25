# VP-A VP-03/04: EXPLAIN on lab Postgres inside QEMU server guest (spec 025 T050 tail).
param(
    [switch]$Help
)
$ErrorActionPreference = "Stop"
if ($Help) {
    Write-Host "Usage: .\scripts\perf\run-qemu-pg-explain.ps1"
    exit 0
}

. "$PSScriptRoot\lib\Invoke-QemuServerGuest.ps1"

$script = @'
set -euo pipefail
cid=$(docker ps --format '{{.Names}}' | grep -E 'postgres-hot|postgres' | head -1)
if [ -z "$cid" ]; then
  echo "[FAIL] postgres container not found"
  exit 1
fi
echo "[OK] postgres container=$cid"
run_sql() {
  label="$1"
  sql="$2"
  echo "=== EXPLAIN $label ==="
  out=$(docker exec "$cid" psql -U avandocmsg -d avandocmsg_hot -At -c "$sql" 2>&1) || {
    echo "[FAIL] $label: $out"
    return 1
  }
  echo "$out"
  if echo "$out" | grep -qiE 'Index Scan|Bitmap Index Scan|Bitmap Heap Scan'; then
    echo "[OK] $label index path present"
  else
    echo "[WARN] $label no index scan in plan (small table?)"
  fi
}
fail=0
run_sql "message_search" "EXPLAIN SELECT id FROM messages WHERE to_tsvector('russian', coalesce(content, '')) @@ plainto_tsquery('russian', 'test');" || fail=1
run_sql "user_trgm" "EXPLAIN SELECT id FROM users WHERE lower(username) LIKE '%adm%' LIMIT 20;" || fail=1
idx=$(docker exec "$cid" psql -U avandocmsg -d avandocmsg_hot -At -c "SELECT indexrelname, idx_scan FROM pg_stat_user_indexes WHERE indexrelname IN ('idx_messages_content_gin','idx_users_username_trgm') ORDER BY 1;" 2>&1) || true
echo "=== index stats ==="
echo "$idx"
if echo "$idx" | grep -q idx_messages_content_gin; then
  echo "[OK] GIN index stats row present"
else
  echo "[WARN] idx_messages_content_gin stats missing (migration V065?)"
fi
exit $fail
'@

$out = Invoke-QemuServerGuest -Script $script
Write-Host $out
if ($out -match '\[FAIL\]') { exit 1 }
Write-Host "[OK] QEMU PG EXPLAIN probe" -ForegroundColor Green
