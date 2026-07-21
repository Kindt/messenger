# Smoke checks for RetentionWorker HTTP (readiness + Prometheus text on the metrics port).
# When to use: retention-worker is up (e.g. docker compose ... --profile retention) and
# RETENTION_METRICS_PORT is set so /health and /metrics are served — default -BaseUrl
# http://localhost:9192 matches docker/docker-compose.dev-min.yml (host 9192 -> container 9191
# when RETENTION_METRICS_PORT=9191 inside the container).
param(
    [string]$BaseUrl = "http://localhost:9192",
    [string]$ApiBaseUrl = ""
)
$ErrorActionPreference = "Stop"

if ($ApiBaseUrl -match ':18080') {
    $scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
    . (Join-Path $scriptDir "lib\Resolve-QemuLabWorkerMetrics.ps1")
    $BaseUrl = (Resolve-QemuLabWorkerMetrics -ApiBaseUrl $ApiBaseUrl).RetentionMetricsUrl -replace '/metrics$', ''
} elseif ($BaseUrl -match ':18080') {
    $scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
    . (Join-Path $scriptDir "lib\Resolve-QemuLabWorkerMetrics.ps1")
    $BaseUrl = (Resolve-QemuLabWorkerMetrics -ApiBaseUrl $BaseUrl).RetentionMetricsUrl -replace '/metrics$', ''
}

function Fail([string]$msg) {
    Write-Host "[FAIL] $msg" -ForegroundColor Red
    exit 1
}

$Root = $BaseUrl.TrimEnd("/")

function Test-RetentionHttpStep {
    param(
        [string]$RelativePath,
        [string]$RequiredSubstring,
        [switch]$IgnoreCase
    )
    $uri = "$Root$RelativePath"
    Write-Host "GET $uri ..." -ForegroundColor Cyan
    try {
        $r = Invoke-WebRequest -Uri $uri -Method Get -UseBasicParsing
    } catch {
        Fail "${RelativePath}: $_"
    }
    if ($r.StatusCode -ne 200) {
        Fail "${RelativePath}: expected HTTP 200, got $($r.StatusCode)"
    }
    $cmp = [StringComparison]::Ordinal
    if ($IgnoreCase) { $cmp = [StringComparison]::OrdinalIgnoreCase }
    if ($r.Content.IndexOf($RequiredSubstring, $cmp) -lt 0) {
        $hint = if ($IgnoreCase) { "case-insensitive" } else { "case-sensitive" }
        Fail "${RelativePath}: body must contain '$RequiredSubstring' ($hint)"
    }
}

Test-RetentionHttpStep -RelativePath "/health" -RequiredSubstring "ok" -IgnoreCase
Test-RetentionHttpStep -RelativePath "/metrics" -RequiredSubstring "retention_worker"
Test-RetentionHttpStep -RelativePath "/metrics" -RequiredSubstring "retention_worker_build_info"

Write-Host "[OK] Retention worker: /health and /metrics (incl. build_info)" -ForegroundColor Green
exit 0
