# Останавливает сервисы профиля web в docker-compose.dev-min.yml (ws-gateway, message-pipeline).
# Help: .\scripts\dev-web-stack-down.ps1 -Help
param(
    [switch]$Volumes,
    [switch]$SkipEnsure,
    [switch]$Help
)

$ErrorActionPreference = "Stop"
if ($Help) {
    Write-Host "Usage: .\scripts\dev-web-stack-down.ps1 [-Volumes] [-SkipEnsure]"
    Write-Host "  docker compose ... --profile web down (опционально -v)."
    Write-Host "  Стек UI (korus-web): останавливайте отдельно — .\scripts\korus-web-down.ps1 (-Attach/-Turn как при up)."
    Write-Host "  Linux/macOS: ./scripts/dev-web-stack-down.sh --help"
    exit 0
}
$Root = Split-Path -Parent $PSScriptRoot
$Lib = Join-Path $PSScriptRoot "lib\korus-env.ps1"
if (-not (Test-Path $Lib)) {
    Write-Error "Missing: $Lib"
}
. $Lib

Set-KorusPathEnvironment -RepoRoot $Root

if (-not (Test-Path $env:KORUS_COMPOSE_DEV_MIN)) {
    Write-Error "Compose file not found: $($env:KORUS_COMPOSE_DEV_MIN)"
}

$skipEnsure = $SkipEnsure -or ($env:SKIP_KORUS_ENSURE -eq "1")
if (-not $skipEnsure) {
    try {
        Invoke-KorusEnsureDevTooling -ScriptsRoot $PSScriptRoot
    } catch {
        Write-Error "Environment setup failed: $_"
    }
}

$dockerArgs = @("compose", "-f", $env:KORUS_COMPOSE_DEV_MIN, "--profile", "web", "down")
if ($Volumes) {
    $dockerArgs += "-v"
}

Write-Host "docker $($dockerArgs -join ' ')" -ForegroundColor Cyan
Invoke-KorusDockerComposeInvoke -DockerArgs $dockerArgs -WorkingDirectory $env:KORUS_REPO_ROOT -Retries 2

Write-Host "[OK] Profile web stopped$(if ($Volumes) { ' (-v)' })" -ForegroundColor Green
Write-Host "If korus-web was up with -Attach: .\scripts\korus-web-down.ps1 -Attach  (-Turn if used)" -ForegroundColor DarkGray
