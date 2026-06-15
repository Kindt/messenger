# Timing audit: compare exist vs missing resource latency (epic 04 / ROADMAP §5).
param(
    [string]$BaseUrl = "http://127.0.0.1:18080",
    [int]$Iterations = 50,
    [double]$MaxDeltaRatio = 0.05,
    [switch]$Help
)
$ErrorActionPreference = "Stop"
if ($Help) {
    Write-Host @"
Usage: .\scripts\audit-timing.ps1 [-BaseUrl url] [-Iterations N] [-MaxDeltaRatio 0.05]

Compares mean latency GET existing vs missing chat (TTFB, response body not fully read). Writes docs/SECURITY_AUDIT.md.
On noisy dev stacks set SECURITY_TIMING_NORMALIZATION_MIN_MS on core-api (220 for QEMU) or pass -MaxDeltaRatio 0.12 for local audit only.
"@
    exit 0
}
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
. "$scriptDir\lib\SmokeMessaging.ps1"

function Measure-MeanMs {
    param([scriptblock]$Call)
    $sum = 0.0
    for ($i = 0; $i -lt $Iterations; $i++) {
        $sw = [System.Diagnostics.Stopwatch]::StartNew()
        try { & $Call | Out-Null } catch {}
        $sw.Stop()
        $sum += $sw.Elapsed.TotalMilliseconds
    }
    return $sum / $Iterations
}

$token = Get-SmokeApiToken -BaseUrl $BaseUrl -User "csadmin" -Pass "csadmin"
$headers = @{ Authorization = "Bearer $token" }
$chatList = Invoke-RestMethod -Uri "$BaseUrl/api/v1/chats" -Headers $headers
$existChat = $null
if ($chatList) {
    $first = if ($chatList -is [System.Array]) { $chatList[0] } else { $chatList }
    if ($first) {
        $existChat = $first.id
        if (-not $existChat) { $existChat = $first.chat_id }
    }
}
if (-not $existChat) {
    $created = Invoke-RestMethod -Uri "$BaseUrl/api/v1/chats" -Method Post -Headers $headers `
        -Body (@{ type = "group"; title = "timing-audit"; member_ids = @() } | ConvertTo-Json) `
        -ContentType "application/json; charset=utf-8"
    $existChat = $created.id
    if (-not $existChat) { $existChat = $created.chat_id }
}
$missingChat = "00000000-0000-4000-8000-000000000000"

function Invoke-TimingGet {
    param([string]$Uri, [hashtable]$Headers)
    $request = [System.Net.HttpWebRequest]::Create($Uri)
    $request.Method = "GET"
    foreach ($key in $Headers.Keys) {
        if ($key -eq "Authorization") {
            $request.Headers["Authorization"] = $Headers[$key]
        } else {
            $request.Headers[$key] = $Headers[$key]
        }
    }
    try {
        $response = $request.GetResponse()
        $response.Close()
    } catch {
        $webResp = $_.Exception.Response
        if ($webResp) { $webResp.Close() }
    }
}

$existMs = Measure-MeanMs {
    Invoke-TimingGet -Uri "$BaseUrl/api/v1/chats/$existChat" -Headers $headers
}
$missingMs = Measure-MeanMs {
    Invoke-TimingGet -Uri "$BaseUrl/api/v1/chats/$missingChat" -Headers $headers
}

$maxMs = [Math]::Max($existMs, $missingMs)
if ($maxMs -lt 1) { $maxMs = 1 }
$delta = [Math]::Abs($existMs - $missingMs) / $maxMs
$report = @"
# Security timing audit

Date: $(Get-Date -Format o)
BaseUrl: $BaseUrl
Iterations: $Iterations

| Probe | Mean ms |
|-------|---------|
| GET existing chat | $([Math]::Round($existMs, 2)) |
| GET missing chat | $([Math]::Round($missingMs, 2)) |
| Relative delta | $([Math]::Round($delta * 100, 2))% |

Threshold: $([Math]::Round($MaxDeltaRatio * 100, 2))%
"@
$reportPath = Join-Path (Split-Path $scriptDir -Parent) "docs\SECURITY_AUDIT.md"
Set-Content -Path $reportPath -Value $report -Encoding utf8
Write-Host $report
if ($delta -gt $MaxDeltaRatio) {
    Write-Host "FAIL: timing delta exceeds threshold" -ForegroundColor Red
    exit 1
}
Write-Host "PASS: timing delta within threshold" -ForegroundColor Green
exit 0
