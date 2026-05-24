# Smoke push-worker health (full-server: :9194, dev-min --profile web: :9193).
param(
    [string]$HealthUrl = "",
    [switch]$Help
)

$ErrorActionPreference = "Stop"
if ($Help) {
    Write-Host "Usage: .\scripts\smoke-push-worker.ps1 [-HealthUrl <url>]"
    Write-Host "  Default probes: http://localhost:9194/health then http://localhost:9193/health."
    exit 0
}

$urls = if ($HealthUrl) { @($HealthUrl) } else { @("http://localhost:9194/health", "http://localhost:9193/health") }
$lastError = $null

foreach ($url in $urls) {
    try {
        $r = Invoke-WebRequest -Uri $url -UseBasicParsing -Method Get
        if ($r.StatusCode -ne 200) {
            throw "status $($r.StatusCode)"
        }
        if ($r.Content.Trim() -ne "ok") {
            throw "body expected 'ok', got: $($r.Content)"
        }
        Write-Host "[OK] push-worker health ($url)" -ForegroundColor Green
        exit 0
    } catch {
        $lastError = $_
    }
}

Write-Host "[FAIL] push-worker health checks failed for: $($urls -join ', ') : $lastError" -ForegroundColor Red
exit 1
