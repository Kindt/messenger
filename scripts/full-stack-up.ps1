# docker/docker-compose.full-server.yml (infra + core-api + ws-gateway + message-pipeline + retention-worker).
# Skip tooling: -SkipEnsure or env SKIP_KORUS_ENSURE=1 (same as full-stack-up.sh --skip-ensure).
# Help: .\scripts\full-stack-up.ps1 -Help
param(
    [switch]$Build,
    [switch]$SkipEnsure,
    [switch]$ExportSmoke,
    [switch]$ExportAutoQueue,
    [switch]$WaitReady,
    [switch]$Help
)

$ErrorActionPreference = "Stop"
if ($Help) {
    Write-Host "Usage: .\scripts\full-stack-up.ps1 [-Build] [-SkipEnsure] [-ExportSmoke] [-ExportAutoQueue] [-WaitReady]"
    Write-Host "  -ExportSmoke applies export + retention-export compose overlays (admin suggest, dry-run retention)."
    Write-Host "  -ExportAutoQueue also enables EXPORT_AUTO_QUEUE_ON_SUGGESTED on core-api."
    Write-Host "  -WaitReady (default with -ExportSmoke): poll health on 8080/9192/9193 after up (push-worker is on 9194)."
    Write-Host "  Env SKIP_KORUS_ENSURE=1 skips tooling. Linux/macOS: ./scripts/full-stack-up.sh --help"
    Write-Host "  After success: hints for korus-web (attach, optional Turn), smoke, full-stack-down."
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

$dockerArgs = @("compose", "-f", $Compose)
if ($ExportSmoke) {
    $dockerArgs += Get-KorusExportSmokeComposeArgs -AutoQueue:$ExportAutoQueue
}
$dockerArgs += @("up", "-d")
if ($Build) { $dockerArgs += "--build" }

Write-Host "docker $($dockerArgs -join ' ') ..." -ForegroundColor Cyan
Push-Location $env:KORUS_REPO_ROOT
try {
    Invoke-KorusDockerComposeInvoke -DockerArgs $dockerArgs -Retries 2
} finally {
    Pop-Location
}

if ($ExportSmoke -and (-not $PSBoundParameters.ContainsKey('WaitReady') -or $WaitReady)) {
    $wait = $true
    if ($PSBoundParameters.ContainsKey('WaitReady') -and -not $WaitReady) { $wait = $false }
    if ($wait) {
        Write-Host "Waiting for export stack health ..." -ForegroundColor Cyan
        & (Join-Path $PSScriptRoot "wait-stack-ready.ps1")
    }
}

Write-Host ""
Write-Host "[OK] Full stack: core-api :8080, Keycloak :8081, ws-gateway :8082, export-replay :9193, retention :9192, push-worker :9194" -ForegroundColor Green
Write-Host "Web Push VAPID: .\scripts\generate-vapid.ps1  (then set keys on push-worker + korus-web WEB_CLIENT_VAPID_PUBLIC_KEY)" -ForegroundColor DarkGray
Write-Host "Admin: http://localhost:8080/admin/  (realm avandocmsg: csadmin/csadmin or admin/admin)"
Write-Host "korus-web same network: .\scripts\korus-web-up.ps1 -Attach -Build   (Linux/macOS: ./scripts/korus-web-up.sh --attach --build)" -ForegroundColor Green
Write-Host "Optional local TURN (coturn) with UI: .\scripts\korus-web-up.ps1 -Attach -Turn -Build   (./scripts/korus-web-up.sh --attach --turn --build)" -ForegroundColor DarkGray
Write-Host "Smoke push-worker: .\scripts\smoke-push-worker.ps1   (./scripts/smoke-push-worker.sh)" -ForegroundColor DarkGray
Write-Host "Smoke korus-web (after UI): .\scripts\smoke-korus-web.ps1 -CheckApi   (./scripts/smoke-korus-web.sh --check-api)" -ForegroundColor DarkGray
Write-Host "Stop full stack: .\scripts\full-stack-down.ps1   (then korus-web-down if UI was up)" -ForegroundColor Green
if ($ExportSmoke) {
    Write-Host "Export pack: .\scripts\smoke-export-compliance-pack.ps1" -ForegroundColor DarkGray
    Write-Host "  one-shot: .\scripts\smoke-export-compliance-stack.ps1 -AutoQueue [-Down]" -ForegroundColor DarkGray
    Write-Host "  observability: http://localhost:9090 (deploy/observability/docker-compose.observability.yml)" -ForegroundColor DarkGray
}
