# Verifies Prometheus metrics on core-api, export-replay, and retention workers.
param(
    [string]$CoreMetricsUrl = "http://localhost:8080/api/v1/metrics/prometheus",
    [string]$WorkerMetricsUrl = "http://localhost:9193/metrics",
    [string]$RetentionMetricsUrl = "http://localhost:9192/metrics"
)
$ErrorActionPreference = "Stop"
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
. (Join-Path $scriptDir "lib\SmokePrometheus.ps1")

function Get-MetricsText {
    param([string]$Url)
    Write-Host "GET $Url" -ForegroundColor DarkGray
    return (Invoke-WebRequest -Uri $Url -UseBasicParsing).Content
}

Write-Host "Core API export metrics ..." -ForegroundColor Cyan
$core = Get-MetricsText -Url $CoreMetricsUrl
Test-PrometheusMetricsPresent -MetricsText $core -RequiredNames @(
    "export_jobs_enqueued_total",
    "export_jobs_cancelled_total",
    "export_jobs_processing_stale"
)
Write-Host "[OK] core-api export metrics" -ForegroundColor Green

Write-Host "Export-replay worker ..." -ForegroundColor Cyan
$worker = Get-MetricsText -Url $WorkerMetricsUrl
Test-PrometheusMetricsPresent -MetricsText $worker -RequiredNames @(
    "export_replay_worker_jobs_started_total",
    "export_replay_worker_jobs_completed_total",
    "export_replay_worker_jobs_cancelled_total"
)
Write-Host "[OK] export-replay worker metrics" -ForegroundColor Green

Write-Host "Retention worker ..." -ForegroundColor Cyan
$ret = Get-MetricsText -Url $RetentionMetricsUrl
Test-PrometheusMetricsPresent -MetricsText $ret -RequiredNames @(
    "retention_worker_export_suggested_published_total",
    "retention_worker_last_hot_body_pass_epoch_seconds"
)
Write-Host "[OK] retention export metrics" -ForegroundColor Green
Write-Host "[OK] export observability smoke" -ForegroundColor Green
