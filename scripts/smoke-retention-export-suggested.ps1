# E2E: retention-worker hot-body pass -> msg.export.suggested -> core-api audit.
# Prereqs: stack up; retention with RETENTION_PUBLISH_EXPORT_SUGGESTED=true and candidates in DB.
# Dev-friendly: docker compose -f docker/docker-compose.full-server.yml `
#   -f docker/docker-compose.retention-export-smoke.yml up -d retention-worker
param(
    [string]$BaseUrl = "http://localhost:8080",
    [string]$RetentionMetricsUrl = "http://localhost:9192/metrics",
    [string]$ChatId = "",
    [switch]$Prepare,
    [switch]$Seed,
    [switch]$CreateGroup,
    [switch]$IncludeFile,
    [int]$MessageCount = 3,
    [int]$WaitSeconds = 180,
    [int]$PollIntervalSec = 5
)
$ErrorActionPreference = "Stop"
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
. (Join-Path $scriptDir "lib\SmokePrometheus.ps1")

if ($Seed) {
    $seedArgs = @{
        BaseUrl            = $BaseUrl
        MessageCount       = $MessageCount
        PrepareRetention   = $Prepare.IsPresent
    }
    if ($ChatId) { $seedArgs["ChatId"] = $ChatId }
    if ($CreateGroup) { $seedArgs["CreateGroup"] = $true }
    if ($IncludeFile) { $seedArgs["IncludeFile"] = $true }
    $lines = & "$scriptDir\seed-retention-hot-body-candidates.ps1" @seedArgs
    if (-not $ChatId -and $lines) {
        $ChatId = ($lines | Select-Object -Last 1).ToString().Trim()
    }
} elseif ($Prepare -and $ChatId) {
    & "$scriptDir\prepare-retention-export-smoke.ps1" -ChatId $ChatId -BaseUrl $BaseUrl
}

if ($BaseUrl -match ':18080') {
    . (Join-Path $scriptDir "lib\Resolve-QemuLabWorkerMetrics.ps1")
    $resolved = Resolve-QemuLabWorkerMetrics -ApiBaseUrl $BaseUrl -RetentionMetricsUrl $RetentionMetricsUrl
    $RetentionMetricsUrl = $resolved.RetentionMetricsUrl
    if ($WaitSeconds -ge 180) { $WaitSeconds = 120 }
}

if ($ChatId -and $BaseUrl -match ':18080' -and -not $Seed) {
    Write-Host "Re-prep retention policy for chat $ChatId ..." -ForegroundColor DarkGray
    & "$scriptDir\prepare-retention-export-smoke.ps1" -ChatId $ChatId -BaseUrl $BaseUrl | Out-Null
    Start-Sleep -Seconds 3
}

& "$scriptDir\smoke-retention-worker.ps1" -BaseUrl ($RetentionMetricsUrl -replace '/metrics$', '')
Write-Host "Waiting for retention hot-body pass (epoch gauge) ..." -ForegroundColor Cyan
$metricsUri = $RetentionMetricsUrl.TrimEnd("/")
if (-not $metricsUri.EndsWith("/metrics")) {
    $metricsUri += "/metrics"
}
$baselineText = (Invoke-WebRequest -Uri $metricsUri -UseBasicParsing).Content
$baselineEpoch = Get-PrometheusGauge -MetricsText $baselineText -Name "retention_worker_last_hot_body_pass_epoch_seconds"
if ($null -eq $baselineEpoch) {
    Write-Host "[WARN] retention_worker_last_hot_body_pass_epoch_seconds not found; continuing" -ForegroundColor Yellow
}

$deadline = (Get-Date).AddSeconds($WaitSeconds)
$passSeen = $false
while ((Get-Date) -lt $deadline) {
    Start-Sleep -Seconds $PollIntervalSec
    $text = (Invoke-WebRequest -Uri $metricsUri -UseBasicParsing).Content
    $epoch = Get-PrometheusGauge -MetricsText $text -Name "retention_worker_last_hot_body_pass_epoch_seconds"
    if ($null -ne $epoch -and ($null -eq $baselineEpoch -or $epoch -gt $baselineEpoch)) {
        Write-Host "[OK] retention pass epoch=$epoch" -ForegroundColor Green
        $passSeen = $true
        break
    }
    Write-Host "  ... waiting (epoch=$epoch)" -ForegroundColor DarkGray
}

if (-not $passSeen) {
    throw "Timed out waiting for retention hot-body pass. Set RETENTION_SCAN_INTERVAL_SECONDS low or use docker-compose.retention-export-smoke.yml"
}

$afterText = (Invoke-WebRequest -Uri $metricsUri -UseBasicParsing).Content
if ($afterText -match "retention_worker_export_suggested_published_total") {
    $pub = Get-PrometheusCounter -MetricsText $afterText -Name "retention_worker_export_suggested_published_total"
    if ($null -ne $pub -and $pub -gt 0) {
        Write-Host "[OK] retention_worker_export_suggested_published_total=$pub" -ForegroundColor Green
    } else {
        Write-Host "[WARN] export_suggested metric present but 0 (no candidates in pass?)" -ForegroundColor Yellow
    }
} else {
    Write-Host "[WARN] retention_worker_export_suggested_published_total not in metrics" -ForegroundColor Yellow
}

$suggestArgs = @{ BaseUrl = $BaseUrl; Limit = 10 }
if ($ChatId) { $suggestArgs["ChatId"] = $ChatId }
& "$scriptDir\smoke-export-suggested.ps1" @suggestArgs
if ($LASTEXITCODE -ne 0) {
    Write-Host "[HINT] Ensure chat has hot-body candidates (old messages, retention policy) or use -ChatId for a chat in the last pass." -ForegroundColor Yellow
    exit 1
}
Write-Host "[OK] retention -> export.suggested audit" -ForegroundColor Green
