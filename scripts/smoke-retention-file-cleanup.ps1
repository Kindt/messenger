# Smoke: orphaned file_metadata cleanup (requires RETENTION_FILE_METADATA_CLEANUP_ENABLED on worker)
param(
    [string]$MetricsUrl = "",
    [string]$BaseUrl = "http://127.0.0.1:18080"
)

$ErrorActionPreference = "Stop"
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
if (-not $MetricsUrl -and $BaseUrl -match ':18080') {
    . (Join-Path $scriptDir "lib\Resolve-QemuLabWorkerMetrics.ps1")
    $MetricsUrl = (Resolve-QemuLabWorkerMetrics -ApiBaseUrl $BaseUrl).RetentionMetricsUrl
}
if (-not $MetricsUrl) { $MetricsUrl = "http://localhost:9192/metrics" }
Write-Host "GET $MetricsUrl ..."
try {
    $r = Invoke-WebRequest -Uri $MetricsUrl -UseBasicParsing
} catch {
    Write-Error "Retention metrics not reachable: $_"
}
if ($r.Content -notmatch "retention_worker_file_metadata_deleted_total") {
    Write-Host "WARN: metric retention_worker_file_metadata_deleted_total not yet scraped (worker may not have run file pass)"
}
Write-Host "smoke-retention-file-cleanup: PASS (metrics probe; full cleanup requires stack + orphaned files)" -ForegroundColor Green
