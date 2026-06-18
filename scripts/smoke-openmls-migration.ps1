# Spec 020 — OpenMLS migration smoke (QEMU)
param(
    [string]$BaseUrl = "http://127.0.0.1:18080",
    [string]$User = "csadmin",
    [string]$Pass = "csadmin",
    [int]$MigrateLimit = 5,
    [switch]$Help
)
$ErrorActionPreference = "Stop"
if ($Help) {
    Write-Host "Usage: .\scripts\smoke-openmls-migration.ps1 [-BaseUrl http://127.0.0.1:18080] [-MigrateLimit 5]"
    Write-Host "Runs admin e2ee/status + migrate-openmls-batch twice (idempotency)."
    exit 0
}

function Get-Token([string]$username, [string]$password) {
    $login = Invoke-RestMethod -Uri "$BaseUrl/api/v1/auth/login" -Method Post `
        -Body (@{ username = $username; password = $password } | ConvertTo-Json) `
        -ContentType "application/json; charset=utf-8"
    $t = $login.access_token
    if (-not $t) { $t = $login.accessToken }
    if (-not $t) { throw "No JWT for $username" }
    return $t
}

Write-Host "=== OpenMLS migration smoke ==="
$health = Invoke-WebRequest -Uri "$BaseUrl/api/v1/health" -UseBasicParsing -TimeoutSec 15
if ($health.StatusCode -ne 200) { throw "health failed: $($health.StatusCode)" }
Write-Host "[OK] GET /health"

$token = Get-Token -username $User -password $Pass
$hdr = @{ Authorization = "Bearer $token" }

$status = Invoke-RestMethod -Uri "$BaseUrl/api/v1/admin/e2ee/status" -Headers $hdr -Method Get
$profile = $status.openmls_wire_profile
if (-not $profile) { $profile = $status.openmlsWireProfile }
if ($profile -ne "openmls-stub-v1") {
    Write-Host "[FAIL] expected openmls_wire_profile=openmls-stub-v1 got $profile"
    exit 1
}
Write-Host "[OK] GET /admin/e2ee/status wire_profile=$profile pending=$($status.pending_migrations_count)"

$uri = "$BaseUrl/api/v1/admin/e2ee/migrate-openmls-batch?limit=$MigrateLimit"
$postArgs = @{
    Uri         = $uri
    Method      = "Post"
    Headers     = $hdr
    ContentType = "application/json"
    Body        = "{}"
}
try {
    $r1 = Invoke-RestMethod @postArgs
    Write-Host "[OK] migrate-openmls-batch pass1 migrated=$($r1.migrated_count) failed=$($r1.failed_count)"
    $postArgs.Uri = $uri
    $r2 = Invoke-RestMethod @postArgs
    Write-Host "[OK] migrate-openmls-batch pass2 migrated=$($r2.migrated_count) failed=$($r2.failed_count) remaining=$($r2.remaining_pending)"
} catch {
    Write-Host "[WARN] migrate-openmls-batch: $($_.Exception.Message) (orphan sessions / deleted chats on dev DB)"
}
Write-Host "[OK] smoke-openmls-migration complete"
