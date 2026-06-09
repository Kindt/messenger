# Timing audit: compare exist vs missing resource latency (epic 04 / ROADMAP §5).
param(
    [string]$BaseUrl = "http://127.0.0.1:18080",
    [int]$Iterations = 50,
    [double]$MaxDeltaRatio = 0.05
)
$ErrorActionPreference = "Stop"
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

$token = Get-SmokeAccessToken -BaseUrl $BaseUrl -Username "csadmin" -Password "csadmin"
$headers = @{ Authorization = "Bearer $token" }
$existChat = (Invoke-RestMethod -Uri "$BaseUrl/api/v1/chats" -Headers $headers).id
$missingChat = "00000000-0000-4000-8000-000000000000"

$existMs = Measure-MeanMs {
    Invoke-WebRequest -Uri "$BaseUrl/api/v1/chats/$existChat" -Headers $headers -Method GET -SkipHttpErrorCheck | Out-Null
}
$missingMs = Measure-MeanMs {
    Invoke-WebRequest -Uri "$BaseUrl/api/v1/chats/$missingChat" -Headers $headers -Method GET -SkipHttpErrorCheck | Out-Null
}

$delta = [Math]::Abs($existMs - $missingMs) / [Math]::Max($existMs, $missingMs, 1)
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
