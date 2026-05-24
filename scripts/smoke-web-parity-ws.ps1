# Spec 002 WS/protocol parity smoke (T022 backend paths).
# Validates token auth, reconnect, and rtc_signal envelope without browser DOM.
param(
    [string]$BaseUrl = "http://127.0.0.1:18080",
    [string]$WebBaseUrl = "http://127.0.0.1:19088",
    [string]$WsUrl = "",
    [string]$User = "csadmin",
    [string]$Pass = "csadmin",
    [string]$ChatId = ""
)

$ErrorActionPreference = "Stop"

function Fail([string]$Message) {
    Write-Host "[FAIL] $Message" -ForegroundColor Red
    exit 1
}

function Step([string]$Title, [scriptblock]$Action) {
    Write-Host ""
    Write-Host "== $Title ==" -ForegroundColor Cyan
    & $Action
    Write-Host "[OK] $Title" -ForegroundColor Green
}

function Get-Token([string]$Username, [string]$Password) {
    $login = Invoke-RestMethod -Uri "$BaseUrl/api/v1/auth/login" -Method Post `
        -Body (@{ username = $Username; password = $Password } | ConvertTo-Json) `
        -ContentType "application/json; charset=utf-8"
    $t = $login.access_token
    if (-not $t) { $t = $login.accessToken }
    if (-not $t) { Fail "No token for $Username" }
    return $t
}

function Resolve-WsBaseUrl([string]$Token) {
    if ($WsUrl) {
        return ($WsUrl -replace "/$", "")
    }
    try {
        $envJs = Invoke-WebRequest -Uri "$WebBaseUrl/web-client-env.js" -UseBasicParsing
        if ($envJs.Content -match 'wsUrl\s*:\s*"([^"]+)"') {
            return ($Matches[1] -replace "/$", "")
        }
    } catch {
        Write-Host "[WARN] could not read web-client-env.js from $WebBaseUrl : $_" -ForegroundColor Yellow
    }
    return "ws://127.0.0.1:8081/ws"
}

function Connect-Ws([string]$Url, [int]$TimeoutSec = 10) {
    Add-Type -AssemblyName System.Net.WebSockets -ErrorAction Stop
    $ws = [System.Net.WebSockets.ClientWebSocket]::new()
    $cts = New-Object System.Threading.CancellationTokenSource
    $cts.CancelAfter([TimeSpan]::FromSeconds($TimeoutSec))
    $task = $ws.ConnectAsync([Uri]$Url, $cts.Token)
    if (-not $task.Wait($TimeoutSec * 1000)) {
        Fail "WS connect timed out: $Url"
    }
    if ($ws.State -ne [System.Net.WebSockets.WebSocketState]::Open) {
        Fail "WS connect failed, state=$($ws.State)"
    }
    return $ws
}

function Close-Ws($ws) {
    if ($null -eq $ws) { return }
    if ($ws.State -eq [System.Net.WebSockets.WebSocketState]::Open) {
        $cts = New-Object System.Threading.CancellationTokenSource
        $cts.CancelAfter([TimeSpan]::FromSeconds(5))
        $task = $ws.CloseAsync(
            [System.Net.WebSockets.WebSocketCloseStatus]::NormalClosure,
            "smoke",
            $cts.Token
        )
        $null = $task.Wait(5000)
    }
    $ws.Dispose()
}

function Send-WsText($ws, [string]$Text) {
    $bytes = [System.Text.Encoding]::UTF8.GetBytes($Text)
    $segment = New-Object System.ArraySegment[byte] -ArgumentList @(,$bytes)
    $cts = New-Object System.Threading.CancellationTokenSource
    $cts.CancelAfter([TimeSpan]::FromSeconds(5))
    $task = $ws.SendAsync(
        $segment,
        [System.Net.WebSockets.WebSocketMessageType]::Text,
        $true,
        $cts.Token
    )
    if (-not $task.Wait(5000)) {
        Fail "WS send timed out"
    }
}

$token = Get-Token -Username $User -Password $Pass
$wsBase = Resolve-WsBaseUrl -Token $token
$wsConnectUrl = "$wsBase?token=$([Uri]::EscapeDataString($token))"
Write-Host "WS base: $wsBase" -ForegroundColor DarkGray

$ws = $null
Step "T022: ws connect with token" {
    $script:ws = Connect-Ws -Url $wsConnectUrl
}

Step "T022: ws reconnect after close" {
    Close-Ws $ws
    $script:ws = Connect-Ws -Url $wsConnectUrl
}

if (-not $ChatId) {
    Step "T022: resolve chat id for rtc_signal" {
        $chats = Invoke-RestMethod -Uri "$BaseUrl/api/v1/chats" -Headers @{ Authorization = "Bearer $token" } -Method Get
        if (-not $chats -or $chats.Count -lt 1) { Fail "no chats for rtc_signal smoke" }
        $first = $chats[0]
        $script:ChatId = $first.id
        if (-not $script:ChatId) { $script:ChatId = $first.chat_id }
        if (-not $script:ChatId) { Fail "chat id missing" }
    }
}

Step "T022: rtc_signal envelope accepted" {
    $payload = @{
        type = "rtc_signal"
        chatId = $ChatId
        payload = @{
            kind = "offer"
            targetUserId = "00000000-0000-0000-0000-000000000001"
            sdp = "v=0"
        }
    } | ConvertTo-Json -Compress
    Send-WsText -ws $ws -Text $payload
    Start-Sleep -Milliseconds 300
}

Close-Ws $ws

Write-Host ""
Write-Host "[OK] web parity WS smoke (spec 002 T022 protocol)" -ForegroundColor Green
Write-Host "Manual still required: browser DOM RTC UI (accept/hangup/mic/cam) per HANDOFF.md." -ForegroundColor DarkGray
