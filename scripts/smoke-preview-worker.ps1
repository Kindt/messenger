# Smoke preview-worker GET /health (full-server :9195).
param(
    [string]$HealthUrl = "http://localhost:9195/health"
)

$ErrorActionPreference = "Stop"
$r = Invoke-WebRequest -Uri $HealthUrl -UseBasicParsing -TimeoutSec 10
if ($r.StatusCode -ne 200) { throw "preview-worker health failed: $($r.StatusCode)" }
Write-Host "[OK] preview-worker health ($HealthUrl)"
