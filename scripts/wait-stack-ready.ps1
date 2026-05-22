# Wait for core-api, retention-worker, and optional export-replay-worker HTTP readiness.
param(
    [string]$BaseUrl = "http://localhost:8080",
    [string]$RetentionBaseUrl = "http://localhost:9192",
    [string]$ExportReplayBaseUrl = "http://localhost:9193",
    [switch]$SkipExportReplay,
    [int]$TimeoutSec = 300,
    [int]$IntervalSec = 5
)
$ErrorActionPreference = "Stop"

$deadline = (Get-Date).AddSeconds($TimeoutSec)
$coreOk = $false
$retOk = $false
$exportOk = $SkipExportReplay.IsPresent

while ((Get-Date) -lt $deadline) {
    if (-not $coreOk) {
        try {
            $r = Invoke-WebRequest -Uri "$BaseUrl/api/v1/health" -UseBasicParsing -TimeoutSec 5
            if ($r.StatusCode -eq 200) {
                Write-Host "[OK] core-api health" -ForegroundColor Green
                $coreOk = $true
            }
        } catch { }
    }
    if (-not $retOk) {
        try {
            $r = Invoke-WebRequest -Uri "$RetentionBaseUrl/health" -UseBasicParsing -TimeoutSec 5
            if ($r.StatusCode -eq 200 -and $r.Content -match "ok") {
                Write-Host "[OK] retention-worker health" -ForegroundColor Green
                $retOk = $true
            }
        } catch { }
    }
    if (-not $exportOk) {
        try {
            $r = Invoke-WebRequest -Uri "$ExportReplayBaseUrl/health" -UseBasicParsing -TimeoutSec 5
            if ($r.StatusCode -eq 200 -and $r.Content -match "ok") {
                Write-Host "[OK] export-replay-worker health" -ForegroundColor Green
                $exportOk = $true
            }
        } catch { }
    }
    if ($coreOk -and $retOk -and $exportOk) { exit 0 }
    Write-Host "  waiting core=$coreOk retention=$retOk export=$exportOk ..." -ForegroundColor DarkGray
    Start-Sleep -Seconds $IntervalSec
}
throw "Timed out (core=$coreOk retention=$retOk export=$exportOk)"
