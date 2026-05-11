# pull + build images. From repo root: .\scripts\create-stand.ps1 [min|full]
# Skip tooling install: -SkipEnsure or env SKIP_KORUS_ENSURE=1 (same as create-stand.sh).
# Help: .\scripts\create-stand.ps1 -Help
param(
    [Parameter(Position = 0)]
    [ValidateSet("min", "full")]
    [string]$Stand = "min",
    [switch]$SkipEnsure,
    [switch]$Help
)

$ErrorActionPreference = "Stop"
if ($Help) {
    Write-Host "Usage: .\scripts\create-stand.ps1 [min|full] [-SkipEnsure]  (default stand: min)"
    Write-Host "  Env SKIP_KORUS_ENSURE=1 skips tooling. Linux/macOS: ./scripts/create-stand.sh --help"
    exit 0
}
$Root = Split-Path -Parent $PSScriptRoot
$Lib = Join-Path $PSScriptRoot "lib\korus-env.ps1"
if (-not (Test-Path $Lib)) {
    Write-Error "Missing: $Lib"
}
. $Lib

Set-KorusPathEnvironment -RepoRoot $Root

$skipEnsure = $SkipEnsure -or ($env:SKIP_KORUS_ENSURE -eq "1")
if (-not $skipEnsure) {
    try {
        Invoke-KorusEnsureDevTooling -ScriptsRoot $PSScriptRoot
    } catch {
        Write-Error "Environment setup failed: $_"
    }
}

Write-Host "=== Creating AvandocMsg stand: $Stand ===" -ForegroundColor Cyan

$compose = if ($Stand -eq "min") { $env:KORUS_COMPOSE_DEV_MIN } else { $env:KORUS_COMPOSE_FULL_SERVER }
if (-not (Test-Path $compose)) {
    Write-Error "Compose file not found: $compose"
}

Invoke-KorusDockerComposeInvoke -DockerArgs @("compose", "-f", $compose, "pull") -WorkingDirectory $env:KORUS_REPO_ROOT -Retries 2
Invoke-KorusDockerComposeInvoke -DockerArgs @("compose", "-f", $compose, "build") -WorkingDirectory $env:KORUS_REPO_ROOT -Retries 2

if ($Stand -eq "min") {
    Write-Host "Stand 'min' created. Run: .\scripts\start.ps1 min" -ForegroundColor Green
} else {
    Write-Host "Stand 'full' (full-server) created. Run: .\scripts\start.ps1 full" -ForegroundColor Green
}
