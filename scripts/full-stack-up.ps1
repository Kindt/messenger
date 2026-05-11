# docker/docker-compose.full-server.yml (infra + core-api + ws-gateway + message-pipeline + retention-worker).
# Skip tooling: -SkipEnsure or env SKIP_KORUS_ENSURE=1 (same as full-stack-up.sh --skip-ensure).
# Help: .\scripts\full-stack-up.ps1 -Help
param(
    [switch]$Build,
    [switch]$SkipEnsure,
    [switch]$Help
)

$ErrorActionPreference = "Stop"
if ($Help) {
    Write-Host "Usage: .\scripts\full-stack-up.ps1 [-Build] [-SkipEnsure]"
    Write-Host "  Env SKIP_KORUS_ENSURE=1 skips tooling. Linux/macOS: ./scripts/full-stack-up.sh --help"
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

$Compose = $env:KORUS_COMPOSE_FULL_SERVER
if (-not (Test-Path $Compose)) {
    Write-Error "Compose file not found: $Compose"
}

Write-Host "docker compose -f $Compose up -d$(if ($Build) { ' --build' })" -ForegroundColor Cyan
Push-Location $env:KORUS_REPO_ROOT
try {
    if ($Build) {
        Invoke-KorusDockerComposeUp -ComposeFile $Compose -Build -Retries 2
    } else {
        Invoke-KorusDockerComposeUp -ComposeFile $Compose -Retries 2
    }
} finally {
    Pop-Location
}

Write-Host ""
Write-Host "[OK] Full stack: core-api :8080, Keycloak :8081, ws-gateway :8082, retention :9192" -ForegroundColor Green
Write-Host "Admin: http://localhost:8080/admin/  (realm avandocmsg: csadmin/csadmin or admin/admin)"
Write-Host "korus-web same network: .\scripts\korus-web-up.ps1 -Attach -Build"
Write-Host "Stop: .\scripts\full-stack-down.ps1"
