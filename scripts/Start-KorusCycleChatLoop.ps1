#Requires -Version 5.1
# Cycle status ticks for Cursor chat (every 5 min, first tick immediate).
param(
    [int]$IntervalSec = 300,
    [switch]$Help
)

$ErrorActionPreference = 'Continue'
$Root = Split-Path -Parent $PSScriptRoot
$RunDir = Join-Path $Root 'deploy\qemu\run'
$StatusScript = Join-Path $Root 'scripts\Write-KorusCycleChatStatus.ps1'
$FeedPath = Join-Path $RunDir 'cycle-chat-feed.txt'
$LatestPath = Join-Path $RunDir 'cycle-chat-latest.txt'

if ($Help) {
    Write-Host 'Usage: .\scripts\Start-KorusCycleChatLoop.ps1 [-IntervalSec 300]'
    Write-Host 'Feed: deploy/qemu/run/cycle-chat-feed.txt'
    exit 0
}

function Emit-CycleChatTick {
    param([int]$TickNum)
    $summary = ''
    try {
        $summary = (& $StatusScript 2>&1 | Out-String).Trim()
    } catch {
        $summary = "status script error: $($_.Exception.Message)"
    }
    if (-not $summary) { $summary = 'no status' }

    $ts = Get-Date -Format 'yyyy-MM-dd HH:mm:ss'
    $human = "[$ts] $summary"
    $human | Set-Content -Path $LatestPath -Encoding UTF8
    $human | Add-Content -Path $FeedPath -Encoding UTF8

    $payload = @{
        tick    = $TickNum
        at      = $ts
        summary = $summary
        prompt  = "Korus unattended cycle tick $TickNum. Status: $summary. Reply to user in Russian briefly: phase, API/UI, guest job, VPP progress."
    }
    $json = ($payload | ConvertTo-Json -Compress -Depth 4)
    [Console]::Out.WriteLine("AGENT_LOOP_TICK_korus_cycle $json")
    [Console]::Out.Flush()
}

Emit-CycleChatTick -TickNum 0
$tick = 0
while ($true) {
    Start-Sleep -Seconds $IntervalSec
    $tick++
    Emit-CycleChatTick -TickNum $tick
}
