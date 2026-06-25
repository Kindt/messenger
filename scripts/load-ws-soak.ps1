# WS soak load (PS-4.1): hold N connections for duration, sample ws-gateway metrics.
# Requires live QEMU stack: API :18080, UI :19088, WS metrics :9198 optional.
param(
    [string]$BaseUrl = "http://127.0.0.1:18080",
    [string]$WebBaseUrl = "http://127.0.0.1:19088",
    [string]$WsUrl = "",
    [string]$MetricsUrl = "http://127.0.0.1:9198/metrics",
    [string]$User = "csadmin",
    [string]$Pass = "csadmin",
    [int]$Connections = 50,
    [int]$DurationSeconds = 300,
    [int]$ConnectTimeoutSec = 15,
    [int]$MaxRssMb = 400
)
$ErrorActionPreference = "Stop"

function Fail([string]$Message) {
    Write-Host "[FAIL] $Message" -ForegroundColor Red
    exit 1
}

function Get-Token {
    $login = Invoke-RestMethod -Uri "$BaseUrl/api/v1/auth/login" -Method Post `
        -Body (@{ username = $User; password = $Pass } | ConvertTo-Json) `
        -ContentType "application/json; charset=utf-8"
    $t = $login.access_token
    if (-not $t) { $t = $login.accessToken }
    if (-not $t) { Fail "No access token" }
    return $t
}

function Resolve-WsBaseUrl {
    param([string]$Override)
    if ($Override) { return ($Override -replace "/$", "") }
    try {
        $envJs = Invoke-WebRequest -Uri "$WebBaseUrl/web-client-env.js" -UseBasicParsing -TimeoutSec 5
        if ($envJs.Content -match 'wsUrl\s*:\s*"([^"]+)"') {
            return ($Matches[1] -replace "/$", "")
        }
    } catch {
        Write-Host "[WARN] web-client-env.js unavailable: $_" -ForegroundColor Yellow
    }
    return "ws://127.0.0.1:19088/ws"
}

function Open-Ws([string]$Url) {
    $ws = New-Object System.Net.WebSockets.ClientWebSocket
    $cts = New-Object System.Threading.CancellationTokenSource
    $cts.CancelAfter([TimeSpan]::FromSeconds($ConnectTimeoutSec))
    $uri = New-Object System.Uri($Url, [System.UriKind]::Absolute)
    $task = $ws.ConnectAsync($uri, $cts.Token)
    if (-not $task.Wait($ConnectTimeoutSec * 1000)) {
        Fail "WS connect timed out"
    }
    if ($ws.State -ne [System.Net.WebSockets.WebSocketState]::Open) {
        Fail "WS state=$($ws.State)"
    }
    return $ws
}

function Read-MetricValue([string]$Body, [string]$Name) {
    foreach ($line in ($Body -split "`n")) {
        if ($line -match "^$Name\s") {
            if ($line -match "\s(\d+(?:\.\d+)?)\s*$") { return [double]$Matches[1] }
        }
    }
    return $null
}

$token = Get-Token
$wsBase = Resolve-WsBaseUrl -Override $WsUrl
$wsUrl = "$wsBase?token=$([Uri]::EscapeDataString($token))"
Write-Host "WS soak: connections=$Connections duration=${DurationSeconds}s url=$wsBase" -ForegroundColor Cyan

$sockets = New-Object System.Collections.Generic.List[object]
try {
    for ($i = 0; $i -lt $Connections; $i++) {
        $sockets.Add((Open-Ws -Url $wsUrl))
        if (($i + 1) % 25 -eq 0) {
            Write-Host "  connected $($i + 1)/$Connections" -ForegroundColor DarkGray
        }
    }
    Write-Host "[OK] opened $($sockets.Count) WS connections" -ForegroundColor Green

    $end = (Get-Date).AddSeconds($DurationSeconds)
    $samples = @()
    while ((Get-Date) -lt $end) {
        Start-Sleep -Seconds 30
        try {
            $metrics = Invoke-WebRequest -Uri $MetricsUrl -UseBasicParsing -TimeoutSec 5
            $open = Read-MetricValue $metrics.Content "ws_active_sessions"
            if ($null -ne $open) {
                $samples += $open
                Write-Host "  ws_active_sessions=$open" -ForegroundColor DarkGray
            }
        } catch {
            Write-Host "  [WARN] metrics unavailable at $MetricsUrl" -ForegroundColor Yellow
        }
    }
} finally {
    foreach ($ws in $sockets) {
        if ($null -eq $ws) { continue }
        try {
            if ($ws.State -eq [System.Net.WebSockets.WebSocketState]::Open) {
                $cts = New-Object System.Threading.CancellationTokenSource
                $cts.CancelAfter([TimeSpan]::FromSeconds(3))
                $null = $ws.CloseAsync(
                    [System.Net.WebSockets.WebSocketCloseStatus]::NormalClosure,
                    "soak",
                    $cts.Token
                ).Wait(3000)
            }
        } catch { }
        $ws.Dispose()
    }
}

Write-Host "[OK] load-ws-soak complete ($Connections conn, ${DurationSeconds}s)" -ForegroundColor Green
Write-Host "Live gate (pilot guest): ws-gateway RSS < ${MaxRssMb}MB - check docker stats ws-gateway on server guest" -ForegroundColor DarkGray
if ($samples.Count -gt 0) {
    $maxOpen = ($samples | Measure-Object -Maximum).Maximum
    Write-Host "Metrics peak ws_active_sessions=$maxOpen (expect >= $Connections when hub healthy)" -ForegroundColor DarkGray
}
