# Partial E2EE staging smoke (T603): health + optional admin migrate-batch/status.
param(
    [Parameter(Mandatory = $true)]
    [string]$BaseUrl,
    [string]$AdminToken = "",
    [int]$MigrateLimit = 1
)

$ErrorActionPreference = "Stop"
$root = $BaseUrl.TrimEnd("/")
$health = Invoke-WebRequest -Uri "$root/api/v1/health" -UseBasicParsing -TimeoutSec 15
if ($health.StatusCode -ne 200) { throw "health failed: $($health.StatusCode)" }
Write-Host "[OK] GET /api/v1/health"

if ([string]::IsNullOrWhiteSpace($AdminToken)) {
    Write-Host "[SKIP] Admin E2EE API (pass -AdminToken for migrate-batch + status)"
    Write-Host "       Manual: docs/review/e2ee-staging-checklist.md rows 4-6"
    exit 0
}
$headers = @{ Authorization = "Bearer $AdminToken" }
$status = Invoke-WebRequest -Uri "$root/api/v1/admin/e2ee/status" -Headers $headers -UseBasicParsing
Write-Host "[OK] GET /admin/e2ee/status -> $($status.StatusCode)"
$body = @{ limit = $MigrateLimit } | ConvertTo-Json
try {
    $migrate = Invoke-WebRequest -Uri "$root/api/v1/admin/e2ee/migrate-batch" -Method POST -Headers $headers `
        -ContentType "application/json" -Body $body -UseBasicParsing
    Write-Host "[OK] POST /admin/e2ee/migrate-batch -> $($migrate.StatusCode)"
} catch {
    Write-Host "[WARN] migrate-batch: $($_.Exception.Message) (may be expected if MLS already active)"
}
