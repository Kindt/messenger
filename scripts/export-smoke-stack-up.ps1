# Apply export + optional auto-queue compose overlays to core-api (after full stack is up).
param(
    [switch]$AutoQueue,
    [switch]$Build
)
$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
$Lib = Join-Path $PSScriptRoot "lib\korus-env.ps1"
. $Lib
Set-KorusPathEnvironment -RepoRoot $Root

$base = $env:KORUS_COMPOSE_FULL_SERVER
$overlays = @(
    (Join-Path $env:KORUS_REPO_ROOT "docker\docker-compose.export-smoke.yml")
)
if ($AutoQueue) {
    $overlays += (Join-Path $env:KORUS_REPO_ROOT "docker\docker-compose.export-auto-queue-smoke.yml")
}

$dockerArgs = @("compose", "-f", $base)
foreach ($o in $overlays) {
    if (-not (Test-Path $o)) { throw "Missing $o" }
    $dockerArgs += @("-f", $o)
}
$dockerArgs += @("up", "-d", "core-api")
if ($Build) { $dockerArgs += "--build" }

Write-Host "docker $($dockerArgs -join ' ') ..." -ForegroundColor Cyan
Push-Location $env:KORUS_REPO_ROOT
try {
    Invoke-KorusDockerComposeInvoke -DockerArgs $dockerArgs -Retries 2
} finally {
    Pop-Location
}
Write-Host "[OK] core-api export smoke overlays applied" -ForegroundColor Green
if ($AutoQueue) {
    Write-Host "  EXPORT_AUTO_QUEUE_ON_SUGGESTED=true" -ForegroundColor DarkGray
}
