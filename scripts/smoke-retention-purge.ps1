# Smoke: hot-row purge (requires live stack + retention worker with RETENTION_HOT_ROW_PURGE_ENABLED=true)
param(
    [string]$BaseUrl = "http://127.0.0.1:9080/api",
    [string]$AdminToken = $env:SMOKE_ADMIN_TOKEN
)

$ErrorActionPreference = "Stop"
if (-not $AdminToken) {
    Write-Error "Set SMOKE_ADMIN_TOKEN (admin bearer token)."
}

$headers = @{ Authorization = "Bearer $AdminToken" }
$status = Invoke-RestMethod -Uri "$BaseUrl/v1/admin/purge/status" -Headers $headers -Method GET
if ($null -eq $status.total_purged) { throw "purge/status missing total_purged" }
Write-Host "purge/status total_purged=$($status.total_purged) pending=$($status.pending_count)"
Write-Host "smoke-retention-purge: PASS (status endpoint; full E2E requires worker pass on stack)"
