# Lightweight integrations online probe (no Gradle). For VPP wait loops.
param(
    [int]$MaxSec = 900,
    [switch]$StartVmIfDown,
    [switch]$RepairGateway,
    [switch]$Help
)

$ErrorActionPreference = 'Continue'
$Root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)

if ($Help) {
    Write-Host 'Usage: .\scripts\vpp\Wait-IntegrationsOnline.ps1 [-MaxSec 900] [-StartVmIfDown] [-RepairGateway]'
    exit 0
}

function Test-Port([int]$Port) {
    $t = Test-NetConnection -ComputerName 127.0.0.1 -Port $Port -WarningAction SilentlyContinue -ErrorAction SilentlyContinue
    return [bool]$t.TcpTestSucceeded
}

function Test-Http([string]$Url) {
    try {
        Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec 5 | Out-Null
        return $true
    } catch { return $false }
}

$deadline = (Get-Date).AddSeconds($MaxSec)
$vmStarted = $false
$repairAttempts = 0
while ((Get-Date) -lt $deadline) {
    if (-not (Test-Port 12223)) {
        if ($StartVmIfDown -and -not $vmStarted) {
            Write-Host '  integrations SSH down - starting VM once...' -ForegroundColor Yellow
            & (Join-Path $Root 'scripts\qemu-integrations-up.ps1')
            $vmStarted = $true
        }
        Start-Sleep -Seconds 20
        continue
    }
    if ($RepairGateway -and $repairAttempts -lt 5 -and -not (Test-Http 'http://127.0.0.1:18190/health')) {
        Write-Host "  gateway down - repair attempt $($repairAttempts + 1)/5..." -ForegroundColor Yellow
        try {
            & (Join-Path $Root 'scripts\vpp\Repair-IntegrationsGateway.ps1')
            if ($LASTEXITCODE -ne 0) {
                Write-Host "  repair exit $LASTEXITCODE (will retry wait loop)" -ForegroundColor DarkYellow
            }
        } catch {
            Write-Host "  repair error: $($_.Exception.Message)" -ForegroundColor DarkYellow
        }
        $repairAttempts++
        Start-Sleep -Seconds 15
    }
    $portsOk = @(18190, 18088, 18093, 18094, 18095, 18096, 18097) | ForEach-Object { Test-Port $_ } | Where-Object { -not $_ }
    if ($portsOk.Count -eq 0 -and (Test-Http 'http://127.0.0.1:18190/health')) {
        Write-Host '[OK] integrations online (gateway + plugin ports)' -ForegroundColor Green
        exit 0
    }
    Write-Host '  waiting integrations stack...' -ForegroundColor DarkGray
    Start-Sleep -Seconds 20
}

Write-Host "[FAIL] integrations not online after $MaxSec s" -ForegroundColor Red
exit 1
