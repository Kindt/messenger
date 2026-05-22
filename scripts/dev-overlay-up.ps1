# Hot-swap: web-dev + dev-overlay/webui bind-mount (korus-web/docker-compose.hotswap.yml).
param(
    [switch]$Build,
    [switch]$SkipEnsure,
    [switch]$Help
)

$ErrorActionPreference = "Stop"
if ($Help) {
    Write-Host "Usage: .\scripts\dev-overlay-up.ps1 [-Build] [-SkipEnsure]"
    Write-Host "  Requires dev-overlay/webui (run dev-overlay-init first). Uses korus-web/.env"
    exit 0
}

$Root = Split-Path -Parent $PSScriptRoot
. (Join-Path $PSScriptRoot "lib\korus-env.ps1")
Set-KorusPathEnvironment -RepoRoot $Root

$overlayUi = Join-Path $env:KORUS_DEV_OVERLAY_DIR "webui\index.html"
if (-not (Test-Path $overlayUi)) {
    Write-Host "dev-overlay/webui empty — running dev-overlay-init.ps1" -ForegroundColor Yellow
    & (Join-Path $PSScriptRoot "dev-overlay-init.ps1") -RepoRoot $Root
}

$skipEnsure = $SkipEnsure -or ($env:SKIP_KORUS_ENSURE -eq "1")
if (-not $skipEnsure) {
    Invoke-KorusEnsureDevTooling -ScriptsRoot $PSScriptRoot
}

$Kw = $env:KORUS_KORUS_WEB_DIR
$hotswap = $env:KORUS_KORUS_WEB_COMPOSE_HOTSWAP
if (-not (Test-Path $hotswap)) { Write-Error "Not found: $hotswap" }

$envFile = Join-Path $Kw ".env"
$dockerArgs = @("compose")
if (Test-Path $envFile) {
    $dockerArgs += @("--env-file", ".env")
}
$dockerArgs += @("-f", "docker-compose.hotswap.yml", "up", "-d")
if ($Build) { $dockerArgs += "--build" }

Write-Host "cd $Kw" -ForegroundColor DarkGray
Write-Host "docker $($dockerArgs -join ' ')" -ForegroundColor Cyan
Invoke-KorusDockerComposeInvoke -DockerArgs $dockerArgs -WorkingDirectory $Kw -Retries 2

$port = 9088
if (Test-Path $envFile) {
    foreach ($line in Get-Content $envFile) {
        if ($line -match '^\s*KORUS_WEB_DEV_PORT\s*=\s*(\d+)') { $port = [int]$Matches[1]; break }
        if ($line -match '^\s*KORUS_WEB_LB_PORT\s*=\s*(\d+)') { $port = [int]$Matches[1] }
    }
}
Write-Host ""
Write-Host "[OK] Hot-swap web-dev on 0.0.0.0:$port — edit dev-overlay/webui/ and refresh browser" -ForegroundColor Green
