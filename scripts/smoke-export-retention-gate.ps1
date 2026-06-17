param(
    [switch]$SkipGradle
)

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
Set-Location $repoRoot

Write-Host "=== Export / retention completeness gate ===" -ForegroundColor Cyan

$failed = 0

$artifacts = @(
    "modules/workers/export-replay/src/main/java/com/avandocmsg/messenger/worker/exportreplay/ExportRetentionPolicyLoader.java",
    "deploy/sql/tenant_rls_policies.sql",
    "modules/core-api/src/main/resources/db/migration/V039__tenant_rls_config.sql"
)
foreach ($rel in $artifacts) {
    $path = Join-Path $repoRoot $rel
    if (-not (Test-Path $path)) {
        Write-Host "[FAIL] missing $rel" -ForegroundColor Red
        $failed++
    } else {
        Write-Host "[OK] $rel" -ForegroundColor Green
    }
}

if (-not $SkipGradle) {
    Write-Host "Running export-replay retention tests..." -ForegroundColor Cyan
    & .\gradlew.bat :modules:workers:export-replay:test --tests "*ExportRetention*" -q
    if ($LASTEXITCODE -ne 0) {
        Write-Host "[FAIL] export-replay ExportRetention* tests" -ForegroundColor Red
        $failed++
    } else {
        Write-Host "[OK] export-replay ExportRetention* tests" -ForegroundColor Green
    }
}

if ($failed -gt 0) {
    Write-Host "Export/retention gate FAILED ($failed)" -ForegroundColor Red
    exit 1
}
Write-Host "Export/retention gate PASSED" -ForegroundColor Green
exit 0
