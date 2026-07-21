# Smoke preview-worker GET /health (full-server :9195).
param(
    [string]$HealthUrl = "http://localhost:9195/health"
)

$ErrorActionPreference = "Stop"
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$base = if ($env:BASE_URL) { $env:BASE_URL } else { "" }
if ($base -match ':18080' -or $HealthUrl -match 'localhost:9195') {
    $qemu = Join-Path $scriptDir "smoke-preview-worker-qemu.ps1"
    if (Test-Path $qemu) {
        & $qemu
        exit $LASTEXITCODE
    }
}

$r = Invoke-WebRequest -Uri $HealthUrl -UseBasicParsing -TimeoutSec 10
if ($r.StatusCode -ne 200) { throw "preview-worker health failed: $($r.StatusCode)" }
Write-Host "[OK] preview-worker health ($HealthUrl)"
