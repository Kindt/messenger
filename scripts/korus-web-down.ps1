# Останавливает стек korus-web: те же -f, что у korus-web-up (-Attach, -Turn).
# Help: .\scripts\korus-web-down.ps1 -Help
param(
    [switch]$Attach,
    [switch]$Turn,
    [switch]$Volumes,
    [switch]$SkipEnsure,
    [switch]$Help
)

$ErrorActionPreference = "Stop"
if ($Help) {
    Write-Host "Usage: .\scripts\korus-web-down.ps1 [-Attach] [-Turn] [-Volumes] [-SkipEnsure]"
    Write-Host "  -Volumes: docker compose down -v (удаляет анонимные тома проекта)."
    Write-Host "  Флаги -Attach / -Turn должны совпадать с теми, с которыми поднимали стек."
    Write-Host "  Только ws-gateway из dev-min (--profile web), без korus-web: .\scripts\dev-web-stack-down.ps1"
    Write-Host "  Linux/macOS: ./scripts/korus-web-down.sh --help"
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
if ($Turn -and -not (Test-Path $env:KORUS_KORUS_WEB_COMPOSE_TURN)) {
    Write-Error "Not found: $($env:KORUS_KORUS_WEB_COMPOSE_TURN)"
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
    $dockerArgs += @("-f", "docker-compose.attach.yml")
}
if ($Turn) {
    $dockerArgs += @("-f", "docker-compose.turn.yml")
}
$dockerArgs += @("down")
if ($Volumes) {
    $dockerArgs += "-v"
}

Write-Host "cd $Kw" -ForegroundColor DarkGray
Write-Host "docker $($dockerArgs -join ' ')" -ForegroundColor Cyan
Invoke-KorusDockerComposeInvoke -DockerArgs $dockerArgs -WorkingDirectory $Kw -Retries 2

Write-Host "[OK] korus-web down$(if ($Attach) { ' (attach)' })$(if ($Turn) { ' (+ turn)' })$(if ($Volumes) { ' (-v)' })" -ForegroundColor Green
