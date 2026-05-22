# Smoke push-worker health (full-server or dev-min --profile web). Default :9193 on host.
param(
    [string]$HealthUrl = "http://localhost:9193/health",
    [switch]$Help
)

$ErrorActionPreference = "Stop"
if ($Help) {
    Write-Host "Usage: .\scripts\smoke-push-worker.ps1 [-HealthUrl <url>]"
    Write-Host "  Expects push-worker with PUSH_METRICS_PORT published as host :9193."
    exit 0
}

try {
    $r = Invoke-WebRequest -Uri $HealthUrl -UseBasicParsing -Method Get
    if ($r.StatusCode -ne 200) {
        throw "status $($r.StatusCode)"
    }
    if ($r.Content.Trim() -ne "ok") {
        throw "body expected 'ok', got: $($r.Content)"
    }
} catch {
    Write-Host "[FAIL] push-worker health $HealthUrl : $_" -ForegroundColor Red
    exit 1
}

Write-Host "[OK] push-worker health ($HealthUrl)" -ForegroundColor Green
