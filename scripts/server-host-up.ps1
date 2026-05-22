# Машина 1: full-server + явная публикация портов в LAN (docker-compose.lan-publish.yml).
# Help: .\scripts\server-host-up.ps1 -Help
param(
    [switch]$Build,
    [switch]$SkipEnsure,
    [switch]$Help
)

$ErrorActionPreference = "Stop"
if ($Help) {
    Write-Host "Usage: .\scripts\server-host-up.ps1 [-Build] [-SkipEnsure]"
    Write-Host "  Two-host dev server. See deploy/two-host/README.md"
    exit 0
}

$Root = Split-Path -Parent $PSScriptRoot
$Lib = Join-Path $PSScriptRoot "lib\korus-env.ps1"
. $Lib
Set-KorusPathEnvironment -RepoRoot $Root

$skipEnsure = $SkipEnsure -or ($env:SKIP_KORUS_ENSURE -eq "1")
if (-not $skipEnsure) {
    Invoke-KorusEnsureDevTooling -ScriptsRoot $PSScriptRoot
}

$base = $env:KORUS_COMPOSE_FULL_SERVER
$lan = $env:KORUS_COMPOSE_LAN_PUBLISH
if (-not (Test-Path $base)) { Write-Error "Not found: $base" }
if (-not (Test-Path $lan)) { Write-Error "Not found: $lan" }

Write-Host "docker compose -f full-server.yml -f lan-publish.yml up -d$(if ($Build) { ' --build' })" -ForegroundColor Cyan
Push-Location $env:KORUS_REPO_ROOT
try {
    if ($Build) {
        Invoke-KorusDockerComposeUp -ComposeFile $base -AdditionalComposeFiles @($lan) -Build -Retries 2
    } else {
        Invoke-KorusDockerComposeUp -ComposeFile $base -AdditionalComposeFiles @($lan) -Retries 2
    }
} finally {
    Pop-Location
}

Write-Host ""
Write-Host "[OK] Server host (LAN publish): core-api :8080, Keycloak :8081, ws-gateway :8082" -ForegroundColor Green
Write-Host "Open firewall 8080, 8082. Web machine: copy deploy/two-host/web.env.example -> korus-web/.env" -ForegroundColor Yellow
Write-Host "Health from LAN: curl http://<SERVER_IP>:8080/api/v1/health"
