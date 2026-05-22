# Stops docker/docker-compose.full-server.yml (containers; volumes kept).
# Help: .\scripts\full-stack-down.ps1 -Help
param(
    [switch]$ExportSmoke,
    [switch]$ExportAutoQueue,
    [switch]$Help
)

$ErrorActionPreference = "Stop"
if ($Help) {
    Write-Host "Usage: .\scripts\full-stack-down.ps1 [-ExportSmoke] [-ExportAutoQueue]"
    Write-Host "  Runs docker compose down on full-server (no -v)."
    Write-Host "  After: stop korus-web separately if needed (.\scripts\korus-web-down.ps1; -Attach/-Turn as used)."
    Write-Host "  If only profile web (ws-gateway): .\scripts\dev-web-stack-down.ps1"
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

$dockerArgs = @("compose", "-f", $env:KORUS_COMPOSE_FULL_SERVER)
if ($ExportSmoke) {
    $dockerArgs += Get-KorusExportSmokeComposeArgs -AutoQueue:$ExportAutoQueue
}
$dockerArgs += "down"
Write-Host "docker $($dockerArgs -join ' ') ..." -ForegroundColor Cyan
Invoke-KorusDockerComposeInvoke -DockerArgs $dockerArgs -WorkingDirectory $env:KORUS_REPO_ROOT -Retries 2

Write-Host "[OK] Full stack stopped." -ForegroundColor Green
Write-Host "If korus-web was running: .\scripts\korus-web-down.ps1  (add -Attach / -Turn if you used them)" -ForegroundColor DarkGray
Write-Host "If only profile web (ws-gateway): .\scripts\dev-web-stack-down.ps1" -ForegroundColor DarkGray
