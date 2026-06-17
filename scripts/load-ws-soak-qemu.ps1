# QEMU live load gate wrapper (PS-4.1): sync repo, run guest/host load scripts.
param(
    [string]$ApiBaseUrl = "http://127.0.0.1:18080",
    [string]$WebBaseUrl = "http://127.0.0.1:19088",
    [int]$WsConnections = 20,
    [int]$WsDurationSeconds = 60,
    [int]$UploadParallel = 3,
    [int]$FanoutBurst = 50,
    [switch]$SkipServerRedeploy,
    [switch]$SkipUpload,
    [switch]$SkipFanout,
    [switch]$SkipWsSoak
)
$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
$RunDir = Join-Path $Root "deploy\qemu\run"
$Plink = Join-Path ${env:ProgramFiles} "PuTTY\plink.exe"

. (Join-Path $Root "deploy\qemu\lib\Update-KorusGuestRepo.ps1")

function Fail([string]$Message) {
    Write-Host "[FAIL] $Message" -ForegroundColor Red
    exit 1
}

if (-not (Test-Path $Plink)) { Fail "plink not found: $Plink" }

$serverHk = Get-KorusEd25519HostKey -SerialPath (Join-Path $RunDir "server-serial.log") -Role server -SshPort 12221
if (-not $serverHk) { Fail "server SSH host key not ready" }

Write-Host "=== QEMU load gate $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss') ===" -ForegroundColor Cyan

if (-not $SkipServerRedeploy) {
    Write-Host "Server redeploy (metrics ports + fresh ws-gateway)..." -ForegroundColor Yellow
    & (Join-Path $Root "scripts\qemu-dev-mode.ps1") -Mode sync-api -Force
    if ($LASTEXITCODE -ne 0) { Fail "sync-api failed (exit $LASTEXITCODE)" }
}

Write-Host "Sync repo on server guest..." -ForegroundColor Yellow
Update-KorusGuestRepo -Role server -SshPort 12221 -HostKey $serverHk -Plink $Plink | Out-Null

if (-not $SkipUpload) {
    Write-Host "Upload load (host)..." -ForegroundColor Yellow
    & (Join-Path $Root "scripts\load-api-upload.ps1") -BaseUrl $ApiBaseUrl -Parallel $UploadParallel -UploadsPerWorker 3 -FileSizeKb 256
    if ($LASTEXITCODE -ne 0) {
        Write-Host "[WARN] upload load had failures (see above)" -ForegroundColor Yellow
    }
}

if (-not $SkipFanout) {
    Write-Host "Fan-out synthetic (server guest)..." -ForegroundColor Yellow
    $fanout = "cd /mnt/korus && BURST=$FanoutBurst BASE_URL=http://127.0.0.1:8080 PIPELINE_METRICS_URL=http://127.0.0.1:9197/metrics bash scripts/load-fanout-synthetic.sh"
    Invoke-PlinkShell -Plink $Plink -HostKey $serverHk -Port 12221 -Script $fanout
}

if (-not $SkipWsSoak) {
    Write-Host "WS soak (server guest -> ws-gateway:8082)..." -ForegroundColor Yellow
    $wsSoak = @"
cd /mnt/korus
CONNECTIONS=$WsConnections DURATION_SEC=$WsDurationSeconds \
BASE_URL=http://127.0.0.1:8080 \
WS_BASE=ws://127.0.0.1:8082/ws \
WS_ORIGIN=http://127.0.0.1:9088 \
METRICS_URL=http://127.0.0.1:9198/metrics \
bash scripts/load-ws-soak.sh
"@
    try {
        Invoke-PlinkShell -Plink $Plink -HostKey $serverHk -Port 12221 -Script $wsSoak
    } catch {
        Write-Host "[WARN] WS soak failed: $_" -ForegroundColor Yellow
        Write-Host "  Hint: after sync-api, verify docker-ws-gateway /ws on guest port 8082" -ForegroundColor DarkGray
    }
}

Write-Host "Metrics probe (host forwards)..." -ForegroundColor Yellow
foreach ($pair in @(@("ws", "http://127.0.0.1:9198/metrics"), @("pipeline", "http://127.0.0.1:9197/metrics"))) {
    try {
        $r = Invoke-WebRequest -Uri $pair[1] -UseBasicParsing -TimeoutSec 5
        Write-Host "  $($pair[0]) metrics: HTTP $($r.StatusCode)" -ForegroundColor Green
    } catch {
        Write-Host "  $($pair[0]) metrics: unavailable at $($pair[1])" -ForegroundColor Yellow
    }
}

Write-Host "[OK] load-ws-soak-qemu complete" -ForegroundColor Green
Write-Host "Manual: docker stats ws-gateway message-pipeline on server guest (RSS/CPU gates)" -ForegroundColor DarkGray
