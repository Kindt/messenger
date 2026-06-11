# Minute QEMU status loop — stdout sentinel wakes Cursor agent for chat reports + auto-remediate.
param(
    [int]$IntervalSeconds = 60,
    [switch]$NoRemediate,
    [switch]$Help
)

$ErrorActionPreference = "Continue"
if ($Help) {
    Write-Host @"
Usage: .\scripts\qemu-status-loop.ps1 [-IntervalSeconds 60] [-NoRemediate]

Runs qemu-status-minute.ps1 every minute. Emits AGENT_LOOP_TICK_qemu_chat for Cursor agent wake.
Snapshot: deploy/qemu/run/status-minute.snapshot.json

For chat reports in Cursor: run this script in a Cursor background terminal (not start-qemu-status-loop hidden).
Stop: Ctrl+C or .\scripts\stop-qemu-status-loop.ps1
"@
    exit 0
}

Set-Location (Split-Path -Parent $PSScriptRoot)
$Root = Split-Path -Parent $PSScriptRoot
$RunDir = Join-Path $Root "deploy\qemu\run"
$ErrLog = Join-Path $RunDir "status-minute-loop.err"
$MinuteScript = Join-Path $PSScriptRoot "qemu-status-minute.ps1"
$minuteArgs = @{ Once = $true }
if ($NoRemediate) { $minuteArgs.NoRemediate = $true }

function Emit-AgentTick {
    param([hashtable]$Report)
    $payload = @{
        prompt = $Report.agentHint
        summaryRu = $Report.summaryRu
        stackReady = $Report.stackReady
        issues = $Report.issues
        remediate = $Report.remediate
        snapshot = "deploy/qemu/run/status-minute.snapshot.json"
        log = "deploy/qemu/run/status-minute.log"
    }
    $json = ($payload | ConvertTo-Json -Compress -Depth 6)
    Write-Output "AGENT_LOOP_TICK_qemu_chat $json"
}

# First report immediately (no initial sleep).
try {
    & $MinuteScript @minuteArgs
    $snapPath = Join-Path $RunDir "status-minute.snapshot.json"
    if (Test-Path $snapPath) {
        $r = Get-Content $snapPath -Raw | ConvertFrom-Json
        Emit-AgentTick -Report @{
            agentHint = [string]$r.agentHint
            summaryRu = [string]$r.summaryRu
            stackReady = [bool]$r.stackReady
            issues = @($r.issues)
            remediate = [string]$r.remediate
        }
    }
} catch {
    "$(Get-Date -Format 'yyyy-MM-dd HH:mm:ss') status-minute error: $_" | Add-Content -Path $ErrLog -Encoding utf8
}

while ($true) {
    Start-Sleep -Seconds $IntervalSeconds
    try {
        & $MinuteScript @minuteArgs
        $snapPath = Join-Path $RunDir "status-minute.snapshot.json"
        if (Test-Path $snapPath) {
            $r = Get-Content $snapPath -Raw | ConvertFrom-Json
            Emit-AgentTick -Report @{
                agentHint = [string]$r.agentHint
                summaryRu = [string]$r.summaryRu
                stackReady = [bool]$r.stackReady
                issues = @($r.issues)
                remediate = [string]$r.remediate
            }
        }
    } catch {
        "$(Get-Date -Format 'yyyy-MM-dd HH:mm:ss') status-minute error: $_" | Add-Content -Path $ErrLog -Encoding utf8
    }
}
