# docker-compose.dev-min.yml with profile web (ws-gateway + message-pipeline).
# Skip tooling: -SkipEnsure or env SKIP_KORUS_ENSURE=1 (same as dev-web-stack-up.sh --skip-ensure).
# Help: .\scripts\dev-web-stack-up.ps1 -Help
param(
    [switch]$Build,
    [switch]$SkipEnsure,
    [switch]$Help
)

$ErrorActionPreference = "Stop"
if ($Help) {
    Write-Host "Usage: .\scripts\dev-web-stack-up.ps1 [-Build] [-SkipEnsure]"
    Write-Host "  Env SKIP_KORUS_ENSURE=1 skips tooling. Linux/macOS: ./scripts/dev-web-stack-up.sh --help"
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

if (-not (Test-Path $env:KORUS_COMPOSE_DEV_MIN)) {
    Write-Error "Compose file not found: $($env:KORUS_COMPOSE_DEV_MIN)"
}

$dockerArgs = @("compose", "-f", $env:KORUS_COMPOSE_DEV_MIN, "--profile", "web", "up", "-d")
if ($Build) {
    $dockerArgs += "--build"
}

Write-Host "docker $($dockerArgs -join ' ')" -ForegroundColor Cyan
Invoke-KorusDockerComposeInvoke -DockerArgs $dockerArgs -WorkingDirectory $env:KORUS_REPO_ROOT -Retries 2

Write-Host ""
Write-Host "[OK] Profile web: ws-gateway (host :8082), message-pipeline, push-worker health :9193" -ForegroundColor Green
Write-Host "Web Push: .\scripts\generate-vapid.ps1  → VAPID in push-worker + korus-web .env" -ForegroundColor DarkGray
Write-Host "Next UI: .\scripts\korus-web-up.ps1 -Build   (Linux/macOS: ./scripts/korus-web-up.sh --build)" -ForegroundColor Green
Write-Host "Attach UI to dev-min: .\scripts\korus-web-up.ps1 -Attach -Build   (./scripts/korus-web-up.sh --attach --build, network korus_messenger_dev_min, see korus-web/README.md)" -ForegroundColor Green
Write-Host "Optional local TURN (coturn): .\scripts\korus-web-up.ps1 -Turn -Build   (./scripts/korus-web-up.sh --turn --build; see korus-web/docker-compose.turn.yml)" -ForegroundColor DarkGray
Write-Host "Smoke push-worker: .\scripts\smoke-push-worker.ps1   (./scripts/smoke-push-worker.sh)" -ForegroundColor DarkGray
Write-Host "Smoke: .\scripts\smoke-korus-web.ps1 -CheckApi   (./scripts/smoke-korus-web.sh --check-api)" -ForegroundColor Green
Write-Host "Stop profile web: .\scripts\dev-web-stack-down.ps1   (Linux/macOS: ./scripts/dev-web-stack-down.sh)" -ForegroundColor DarkGray
Write-Host "Core API: curl http://localhost:8080/api/v1/health" -ForegroundColor Green
& (Join-Path $PSScriptRoot "dev-ui-hints.ps1") -RepoRoot $Root
Write-Host "Then run .\scripts\korus-web-up.ps1 -Build and open the web client URL from the hints above." -ForegroundColor DarkGray
