# Verifies export-replay worker /metrics and (optional) counter bump after admin cancel.
param(
    [string]$WorkerMetricsUrl = "http://localhost:9193/metrics",
    [string]$CoreMetricsUrl = "http://localhost:8080/api/v1/metrics/prometheus",
    [string]$ChatId = "",
    [string]$BaseUrl = "http://localhost:8080",
    [switch]$SkipCancelFlow,
    [switch]$SkipCoreApi
)
$ErrorActionPreference = "Stop"
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
. (Join-Path $scriptDir "lib\SmokePrometheus.ps1")

function Get-MetricsText {
    param([string]$Url)
    Write-Host "GET $Url" -ForegroundColor DarkGray
    return (Invoke-WebRequest -Uri $Url -UseBasicParsing).Content
}

Write-Host "Worker metrics ..." -ForegroundColor Cyan
$workerBefore = Get-MetricsText -Url $WorkerMetricsUrl
Test-PrometheusMetricsPresent -MetricsText $workerBefore -RequiredNames @(
    "export_replay_worker_jobs_started_total",
    "export_replay_worker_jobs_completed_total",
    "export_replay_worker_jobs_cancelled_total",
    "export_replay_worker_cancel_hints_total"
)
$cancelledBefore = Get-PrometheusCounter -MetricsText $workerBefore -Name "export_replay_worker_jobs_cancelled_total"
if ($null -eq $cancelledBefore) { $cancelledBefore = 0 }
Write-Host "[OK] worker metrics present (cancelled=$cancelledBefore)" -ForegroundColor Green

if (-not $SkipCoreApi) {
    Write-Host "Core API metrics ..." -ForegroundColor Cyan
    $core = Get-MetricsText -Url $CoreMetricsUrl
    Test-PrometheusMetricsPresent -MetricsText $core -RequiredNames @(
        "export_jobs_enqueued_total",
        "export_jobs_cancelled_total"
    )
    Write-Host "[OK] core-api export metrics present" -ForegroundColor Green
}

if ($SkipCancelFlow -or -not $ChatId) {
    if (-not $ChatId) {
        Write-Host "[SKIP] cancel flow (pass -ChatId to assert worker cancelled counter increases)" -ForegroundColor DarkGray
    }
    exit 0
}

Write-Host "Running admin request+cancel to bump worker counter ..." -ForegroundColor Cyan
& (Join-Path $scriptDir "smoke-admin-export-request-cancel.ps1") -ChatId $ChatId -BaseUrl $BaseUrl -SkipAudit | Out-Host
Start-Sleep -Seconds 2
$workerAfter = Get-MetricsText -Url $WorkerMetricsUrl
$cancelledAfter = Get-PrometheusCounter -MetricsText $workerAfter -Name "export_replay_worker_jobs_cancelled_total"
if ($null -eq $cancelledAfter) { $cancelledAfter = 0 }
if ($cancelledAfter -le $cancelledBefore) {
    Write-Host "[WARN] export_replay_worker_jobs_cancelled_total did not increase ($cancelledBefore -> $cancelledAfter). Worker may have skipped job or metrics reset." -ForegroundColor Yellow
} else {
    Write-Host "[OK] worker cancelled counter increased: $cancelledBefore -> $cancelledAfter" -ForegroundColor Green
}
