# korus-web stack (docker compose in korus-web/). -Attach: docker-compose.attach.yml (network korus_messenger_dev_min).
# Skip tooling: -SkipEnsure or env SKIP_KORUS_ENSURE=1 (same as korus-web-up.sh --skip-ensure).
# Help: .\scripts\korus-web-up.ps1 -Help
param(
    [switch]$Attach,
    [switch]$Build,
    [switch]$SkipEnsure,
    [switch]$Help
)

$ErrorActionPreference = "Stop"
if ($Help) {
    Write-Host "Usage: .\scripts\korus-web-up.ps1 [-Attach] [-Build] [-SkipEnsure]"
    Write-Host "  Env SKIP_KORUS_ENSURE=1 skips tooling. Linux/macOS: ./scripts/korus-web-up.sh --help"
    exit 0
}
$Root = Split-Path -Parent $PSScriptRoot
$Lib = Join-Path $PSScriptRoot "lib\korus-env.ps1"
if (-not (Test-Path $Lib)) {
    Write-Error "Missing: $Lib"
}
. $Lib

Set-KorusPathEnvironment -RepoRoot $Root
$Kw = $env:KORUS_KORUS_WEB_DIR

if (-not (Test-Path $env:KORUS_KORUS_WEB_COMPOSE)) {
    Write-Error "Not found: $($env:KORUS_KORUS_WEB_COMPOSE)"
}
if ($Attach -and -not (Test-Path $env:KORUS_KORUS_WEB_COMPOSE_ATTACH)) {
    Write-Error "Not found: $($env:KORUS_KORUS_WEB_COMPOSE_ATTACH)"
}

$skipEnsure = $SkipEnsure -or ($env:SKIP_KORUS_ENSURE -eq "1")
if (-not $skipEnsure) {
    try {
        Invoke-KorusEnsureDevTooling -ScriptsRoot $PSScriptRoot
    } catch {
        Write-Error "Environment setup failed: $_"
    }
}

$envFile = Join-Path $Kw ".env"
$dockerArgs = @("compose")
if (Test-Path $envFile) {
    $dockerArgs += @("--env-file", ".env")
}
$dockerArgs += @("-f", "docker-compose.yml")
if ($Attach) {
    $null = docker network inspect korus_messenger_dev_min 2>$null
    if ($LASTEXITCODE -ne 0) {
        Write-Warning "Network korus_messenger_dev_min not found. Start dev-min (e.g. .\scripts\dev-web-stack-up.ps1) or set KORUS_DEV_MIN_NETWORK in korus-web\.env."
    }
    Write-Host "Attach: dev-min must be up (default network korus_messenger_dev_min)." -ForegroundColor Yellow
    $dockerArgs += @("-f", "docker-compose.attach.yml")
}
$dockerArgs += @("up", "-d")
if ($Build) {
    $dockerArgs += "--build"
}

Write-Host "cd $Kw" -ForegroundColor DarkGray
Write-Host "docker $($dockerArgs -join ' ')" -ForegroundColor Cyan
Invoke-KorusDockerComposeInvoke -DockerArgs $dockerArgs -WorkingDirectory $Kw -Retries 2

Write-Host ""
Write-Host "[OK] korus-web up$(if ($Attach) { ' (attach)' })" -ForegroundColor Green
Write-Host "Smoke: .\scripts\smoke-korus-web.ps1 -CheckApi  (Linux/macOS: ./scripts/smoke-korus-web.sh --check-api)"
& (Join-Path $PSScriptRoot "dev-ui-hints.ps1") -RepoRoot $Root
