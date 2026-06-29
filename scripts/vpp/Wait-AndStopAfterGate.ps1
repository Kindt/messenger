#Requires -Version 5.1
# Wait for a VPP gate PASS/FAIL, then checkpoint + stop lab + optional QEMU down.
param(
    [Parameter(Mandatory)][string]$GateId,
    [int]$PollSec = 30,
    [int]$MaxMinutes = 180,
    [switch]$QemuDown,
    [switch]$Help
)

$ErrorActionPreference = 'Stop'
$Root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$EvDir = Join-Path $Root 'deploy\qemu\run\vpp-evidence'
$eventsPath = Join-Path $EvDir 'vpp-gate-events.jsonl'
$deadline = (Get-Date).AddMinutes($MaxMinutes)

if ($Help) {
    Write-Host 'Usage: Wait-AndStopAfterGate.ps1 -GateId playwright_matrix_l4_plus_plus [-QemuDown]'
    exit 0
}

Write-Host "[wait] gate=$GateId until PASS/FAIL (max ${MaxMinutes}m, poll ${PollSec}s)" -ForegroundColor Cyan

while ((Get-Date) -lt $deadline) {
    if (Test-Path $eventsPath) {
        $lines = @(Get-Content -LiteralPath $eventsPath -Tail 80 -ErrorAction SilentlyContinue)
        foreach ($line in ($lines | Select-Object -Last 20)) {
            if ($line -notmatch '"gate"\s*:\s*"' + [regex]::Escape($GateId) + '"') { continue }
            if ($line -match '"status"\s*:\s*"(PASS|FAIL)"') {
                $status = $Matches[1]
                Write-Host "[wait] $GateId -> $status" -ForegroundColor $(if ($status -eq 'PASS') { 'Green' } else { 'Red' })
                Start-Sleep -Seconds 5
                & (Join-Path $PSScriptRoot 'Save-VppCheckpoint.ps1') -Reason "auto-stop after $GateId $status"
                & (Join-Path $PSScriptRoot 'Stop-VppLabRun.ps1') -Force
                if ($QemuDown) {
                    & (Join-Path $Root 'scripts\qemu-down.ps1')
                }
                exit $(if ($status -eq 'PASS') { 0 } else { 1 })
            }
        }
    }
    $pw = $null
    try { $pw = & (Join-Path $PSScriptRoot 'Get-VppPlaywrightProgress.ps1') } catch { }
    if ($pw -and $pw.active) {
        Write-Host ("[wait] $(Get-Date -Format HH:mm:ss) PW {0}/{1}" -f $pw.test_index, $pw.test_total) -ForegroundColor DarkGray
    } else {
        Write-Host ("[wait] $(Get-Date -Format HH:mm:ss) gate running...") -ForegroundColor DarkGray
    }
    Start-Sleep -Seconds $PollSec
}

Write-Host "[wait] TIMEOUT waiting for $GateId" -ForegroundColor Red
exit 2
