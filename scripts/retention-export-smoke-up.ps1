# Recreate retention-worker with export-suggested smoke env (dry-run, fast scan).
param(
    [switch]$Build
)
$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
$Lib = Join-Path $PSScriptRoot "lib\korus-env.ps1"
. $Lib
Set-KorusPathEnvironment -RepoRoot $Root

$base = $env:KORUS_COMPOSE_FULL_SERVER
$overlay = Join-Path $env:KORUS_REPO_ROOT "docker\docker-compose.retention-export-smoke.yml"
if (-not (Test-Path $overlay)) { throw "Missing $overlay" }

Write-Host "docker compose up retention-worker (export-suggested smoke overlay) ..." -ForegroundColor Cyan
Push-Location $env:KORUS_REPO_ROOT
try {
    $dockerArgs = @("compose", "-f", $base, "-f", $overlay, "up", "-d", "retention-worker")
    if ($Build) { $dockerArgs += "--build" }
    Invoke-KorusDockerComposeInvoke -DockerArgs $dockerArgs -Retries 2
} finally {
    Pop-Location
}
Write-Host "[OK] retention-worker: RETENTION_PUBLISH_EXPORT_SUGGESTED=true, RETENTION_DRY_RUN=true" -ForegroundColor Green
Write-Host "Then: .\scripts\prepare-retention-export-smoke.ps1 -ChatId <uuid>" -ForegroundColor DarkGray
Write-Host "      .\scripts\smoke-retention-export-suggested.ps1 -ChatId <uuid> -Prepare" -ForegroundColor DarkGray
