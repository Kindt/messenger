# Append immediate PASS/FAIL line for chat + jsonl (spec 030 live gate feed).
param(
    [Parameter(Mandatory)][string]$GateId,
    [Parameter(Mandatory)][ValidateSet('PASS', 'FAIL', 'START', 'RETRY')]
    [string]$Status,
    [int]$ExitCode = 0,
    [int]$PassCount = 0,
    [int]$TotalGates = 145,
    [string]$Detail = "",
    [switch]$Help
)

$ErrorActionPreference = 'Continue'
if ($Help) {
    Write-Host 'Usage: Write-VppGateEvent.ps1 -GateId stack_health -Status PASS -PassCount 5'
    exit 0
}

$Root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$EvDir = Join-Path $Root 'deploy\qemu\run\vpp-evidence'
if (-not (Test-Path $EvDir)) { New-Item -ItemType Directory -Path $EvDir -Force | Out-Null }

. (Join-Path $Root 'scripts\vpp\Get-VppGateLabelRu.ps1')
$label = Get-VppGateLabelRu -GateId $GateId
$ts = Get-Date -Format 'yyyy-MM-dd HH:mm:ss.fff'
$pct = if ($TotalGates -gt 0) { [math]::Round(($PassCount / $TotalGates) * 100, 1) } else { 0 }

$icon = switch ($Status) {
    'PASS' { '[OK]' }
    'FAIL' { '[FAIL]' }
    'RETRY' { '[RETRY]' }
    default { '[START]' }
}

$line = "$ts $icon $GateId ($label) | $PassCount/$TotalGates ($pct%)"
if ($ExitCode -ne 0) { $line += " exit=$ExitCode" }
if ($Detail) { $line += " | $Detail" }

$jsonl = Join-Path $EvDir 'vpp-gate-events.jsonl'
$chatGates = Join-Path $EvDir 'vpp-chat-gates.txt'
$chatLatest = Join-Path $EvDir 'vpp-chat-latest.txt'

$obj = [ordered]@{
    at_local = $ts
    gate = $GateId
    gate_ru = $label
    status = $Status
    exit_code = $ExitCode
    pass_count = $PassCount
    total_gates = $TotalGates
    detail = $Detail
}
Add-Content -Path $jsonl -Value ($obj | ConvertTo-Json -Compress) -Encoding utf8 -ErrorAction SilentlyContinue

$prev = @()
if (Test-Path $chatGates) {
    $prev = @(Get-Content -LiteralPath $chatGates -Tail 80 -ErrorAction SilentlyContinue)
}
$header = ('## VPP gates (live) - last updated ' + $ts)
$body = @($header, '') + $prev + @($line)
if ($body.Count -gt 85) { $body = @($header, '') + $body[-83..-1] }
Set-Content -Path $chatGates -Value ($body -join [Environment]::NewLine) -Encoding utf8 -ErrorAction SilentlyContinue

$stamp = switch ($Status) {
    'PASS' { 'PASS' }
    'FAIL' { 'FAIL' }
    'RETRY' { 'RETRY' }
    default { 'GATE' }
}
Add-Content -Path $chatLatest -Value ([Environment]::NewLine + '**' + $stamp + '** ' + $line) -Encoding utf8 -ErrorAction SilentlyContinue

Write-Host $line -ForegroundColor $(if ($Status -eq 'PASS') { 'Green' } elseif ($Status -eq 'FAIL') { 'Red' } else { 'Yellow' })
