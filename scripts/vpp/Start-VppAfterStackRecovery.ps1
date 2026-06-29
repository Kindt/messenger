# Wait for API + integrations after core-api recreate, then run VPP until-green.
$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)

Write-Host "Waiting for API :18080 (up to 90 min)..." -ForegroundColor Cyan
$apiDeadline = (Get-Date).AddMinutes(90)
$apiOk = $false
while ((Get-Date) -lt $apiDeadline) {
    try {
        $h = Invoke-RestMethod -Uri "http://127.0.0.1:18080/api/v1/health" -TimeoutSec 5
        if ($h.status) {
            Write-Host "API OK: $($h.status)" -ForegroundColor Green
            $apiOk = $true
            break
        }
    } catch {
        Write-Host "  API wait: $($_.Exception.Message)" -ForegroundColor DarkGray
    }
    Start-Sleep -Seconds 30
}
if (-not $apiOk) {
    Write-Host "[FAIL] API not ready after 90 min" -ForegroundColor Red
    exit 1
}

Write-Host "Waiting for integrations (up to 15 min)..." -ForegroundColor Cyan
& (Join-Path $Root "scripts\vpp\Wait-IntegrationsOnline.ps1") -MaxSec 900 -StartVmIfDown -RepairGateway
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

$env:KORUS_QEMU_THREE_VM = "1"
$env:VPP_CHAT_REPORT_SEC = "300"
$env:VPP_RETRY_DELAY_SEC = "120"
& (Join-Path $Root "scripts\run-vpp-until-green.ps1") -Level full -MaxAttempts 10
