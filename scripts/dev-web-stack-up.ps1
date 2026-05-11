# docker-compose.dev-min.yml with profile web (ws-gateway + message-pipeline).
param(
    [switch]$Build,
    [switch]$SkipEnsure
)

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
$Lib = Join-Path $PSScriptRoot "lib\korus-env.ps1"
if (-not (Test-Path $Lib)) {
    Write-Error "Missing: $Lib"
}
. $Lib

Set-KorusPathEnvironment -RepoRoot $Root

if (-not $SkipEnsure) {
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
Write-Host "[OK] Profile web: ws-gateway (host :8082), message-pipeline" -ForegroundColor Green
Write-Host "Next UI: .\scripts\korus-web-up.ps1 -Build   (Linux/macOS: ./scripts/korus-web-up.sh --build)" -ForegroundColor Green
Write-Host "Attach UI to dev-min: .\scripts\korus-web-up.ps1 -Attach -Build   (./scripts/korus-web-up.sh --attach --build, network korus_messenger_dev_min, see korus-web/README.md)" -ForegroundColor Green
Write-Host "Smoke: .\scripts\smoke-korus-web.ps1 -CheckApi   (./scripts/smoke-korus-web.sh --check-api)" -ForegroundColor Green
Write-Host "Core API: curl http://localhost:8080/api/v1/health" -ForegroundColor Green
& (Join-Path $PSScriptRoot "dev-ui-hints.ps1") -RepoRoot $Root
Write-Host "Then run .\scripts\korus-web-up.ps1 -Build and open the web client URL from the hints above." -ForegroundColor DarkGray
