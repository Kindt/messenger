# Stops docker/docker-compose.full-server.yml (containers; volumes kept).
# Help: .\scripts\full-stack-down.ps1 -Help
param([switch]$Help)

$ErrorActionPreference = "Stop"
if ($Help) {
    Write-Host "Usage: .\scripts\full-stack-down.ps1"
    Write-Host "  Runs docker compose down on full-server (no -v)."
    Write-Host "  cmd.exe: scripts\full-stack-down.cmd -Help   Linux/macOS: ./scripts/full-stack-down.sh --help"
    exit 0
}
$Root = Split-Path -Parent $PSScriptRoot
$Lib = Join-Path $PSScriptRoot "lib\korus-env.ps1"
if (-not (Test-Path $Lib)) {
    Write-Error "Missing: $Lib"
}
. $Lib

Set-KorusPathEnvironment -RepoRoot $Root

if (-not (Test-Path $env:KORUS_COMPOSE_FULL_SERVER)) {
    Write-Error "Compose file not found: $($env:KORUS_COMPOSE_FULL_SERVER)"
}

Write-Host "docker compose -f $($env:KORUS_COMPOSE_FULL_SERVER) down" -ForegroundColor Cyan
Invoke-KorusDockerComposeDown -ComposeFile $env:KORUS_COMPOSE_FULL_SERVER -WorkingDirectory $env:KORUS_REPO_ROOT -Retries 2

Write-Host "[OK] Full stack stopped." -ForegroundColor Green
