# Timing audit: compare exist vs missing resource latency (epic 04 / spec 014 S2-1).
param(
    [string]$BaseUrl = "http://127.0.0.1:18080",
    [int]$Iterations = 50,
    [int]$AuthIterations = 15,
    [double]$MaxDeltaRatio = 0.05,
    [switch]$Help
)
$ErrorActionPreference = "Stop"
if ($Help) {
    Write-Host @"
Usage: .\scripts\audit-timing.ps1 [-BaseUrl url] [-Iterations N] [-MaxDeltaRatio 0.05]

Probes: GET chat exist/miss, GET user me/miss, GET message exist/miss, POST login bad-user vs bad-password (fewer iterations).
Writes docs/SECURITY_AUDIT.md. On noisy dev stacks set SECURITY_TIMING_NORMALIZATION_MIN_MS (220 for QEMU).
"@
    exit 0
}
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
. "$scriptDir\lib\SmokeMessaging.ps1"

function Measure-MeanMs {
    param([scriptblock]$Call, [int]$Count = $Iterations)
    $sum = 0.0
    for ($i = 0; $i -lt $Count; $i++) {
        $sw = [System.Diagnostics.Stopwatch]::StartNew()
        try { & $Call | Out-Null } catch {}
        $sw.Stop()
        $sum += $sw.Elapsed.TotalMilliseconds
    }
    return $sum / $Count
}

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

function Invoke-TimingPostJson {
    param([string]$Uri, [string]$Body)
    $request = [System.Net.HttpWebRequest]::Create($Uri)
    $request.Method = "POST"
    $request.ContentType = "application/json; charset=utf-8"
    $bytes = [System.Text.Encoding]::UTF8.GetBytes($Body)
    $request.ContentLength = $bytes.Length
    try {
        $stream = $request.GetRequestStream()
        $stream.Write($bytes, 0, $bytes.Length)
        $stream.Close()
        $response = $request.GetResponse()
        $response.Close()
    } catch {
        $webResp = $_.Exception.Response
        if ($webResp) { $webResp.Close() }
    }
}

function Test-TimingPair {
    param(
        [string]$Name,
        [scriptblock]$ExistCall,
        [scriptblock]$MissingCall,
        [int]$Count = $Iterations
    )
    $existMs = Measure-MeanMs -Call $ExistCall -Count $Count
    $missingMs = Measure-MeanMs -Call $MissingCall -Count $Count
    $maxMs = [Math]::Max($existMs, $missingMs)
    if ($maxMs -lt 1) { $maxMs = 1 }
    $delta = [Math]::Abs($existMs - $missingMs) / $maxMs
    return [PSCustomObject]@{
        Name    = $Name
        ExistMs = [Math]::Round($existMs, 2)
        MissMs  = [Math]::Round($missingMs, 2)
        Delta   = [Math]::Round($delta * 100, 2)
    }
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
$missingUser = "00000000-0000-4000-8000-000000000001"
$missingMessage = "00000000-0000-4000-8000-000000000002"
$existMessage = $null
try {
    $msgList = Invoke-RestMethod -Uri "$BaseUrl/api/v1/chats/$existChat/messages?limit=1" -Headers $headers
    if ($msgList) {
        $firstMsg = if ($msgList -is [System.Array]) { $msgList[0] } else { $msgList }
        if ($firstMsg) {
            $existMessage = $firstMsg.id
            if (-not $existMessage) { $existMessage = $firstMsg.message_id }
        }
    }
} catch {
    $existMessage = $null
}
if (-not $existMessage) {
    try {
        $sent = Invoke-RestMethod -Uri "$BaseUrl/api/v1/chats/$existChat/messages" -Method Post -Headers $headers `
            -Body (@{ body = "timing-audit-probe" } | ConvertTo-Json) `
            -ContentType "application/json; charset=utf-8"
        $existMessage = $sent.id
        if (-not $existMessage) { $existMessage = $sent.message_id }
    } catch {
        $existMessage = $null
    }
}

$rows = @(
    (Test-TimingPair -Name "GET chat" -ExistCall {
        Invoke-TimingGet -Uri "$BaseUrl/api/v1/chats/$existChat" -Headers $headers
    } -MissingCall {
        Invoke-TimingGet -Uri "$BaseUrl/api/v1/chats/$missingChat" -Headers $headers
    }),
    (Test-TimingPair -Name "GET user" -ExistCall {
        Invoke-TimingGet -Uri "$BaseUrl/api/v1/users/me" -Headers $headers
    } -MissingCall {
        Invoke-TimingGet -Uri "$BaseUrl/api/v1/users/$missingUser" -Headers $headers
    }),
    (Test-TimingPair -Name "GET message" -ExistCall {
        if ($existMessage) {
            Invoke-TimingGet -Uri "$BaseUrl/api/v1/chats/$existChat/messages/$existMessage" -Headers $headers
        }
    } -MissingCall {
        Invoke-TimingGet -Uri "$BaseUrl/api/v1/chats/$existChat/messages/$missingMessage" -Headers $headers
    }),
    (Test-TimingPair -Name "POST login" -Count $AuthIterations -ExistCall {
        $body = (@{ username = "csadmin"; password = "wrong-pass-timing" } | ConvertTo-Json)
        Invoke-TimingPostJson -Uri "$BaseUrl/api/v1/auth/login" -Body $body
    } -MissingCall {
        $body = (@{ username = "no_such_user_timing_x"; password = "wrong" } | ConvertTo-Json)
        Invoke-TimingPostJson -Uri "$BaseUrl/api/v1/auth/login" -Body $body
    })
)

$worst = ($rows | Sort-Object { [double]$_.Delta } -Descending | Select-Object -First 1)
$tableLines = $rows | ForEach-Object {
    "| $($_.Name) exist | $($_.ExistMs) | $($_.MissMs) | $($_.Delta)% |"
}
$tableHeader = @"
| Probe | Exist ms | Missing ms | Delta |
|-------|----------|------------|-------|
"@

$report = @"
# Security timing audit

Date: $(Get-Date -Format o)
BaseUrl: $BaseUrl
Iterations: $Iterations (auth: $AuthIterations)

$tableHeader
$($tableLines -join "`n")

Worst probe: $($worst.Name) ($($worst.Delta)%)
Threshold: $([Math]::Round($MaxDeltaRatio * 100, 2))%
"@
$reportPath = Join-Path (Split-Path $scriptDir -Parent) "docs\SECURITY_AUDIT.md"
Set-Content -Path $reportPath -Value $report -Encoding utf8
Write-Host $report

if ([double]$worst.Delta / 100 -gt $MaxDeltaRatio) {
    Write-Host "FAIL: $($worst.Name) timing delta exceeds threshold" -ForegroundColor Red
    exit 1
}
Write-Host "PASS: all timing probes within threshold" -ForegroundColor Green
exit 0
