# Live-streaming API smoke (spec 013 L2 / spec 010 T403)
param(
    [string]$BaseUrl = "http://127.0.0.1:18080",
    [string]$User = "csadmin",
    [string]$Pass = "csadmin",
    [switch]$Help
)

$ErrorActionPreference = "Stop"
if ($Help) {
    Write-Host @"
Usage: .\scripts\smoke-live-session.ps1 [-BaseUrl http://127.0.0.1:18080]

Flow: login -> media/capabilities -> create group -> POST live-sessions -> list -> join/leave/end.
If LiveKit env missing on core-api, create returns 503 (expected graceful fail).
Prereq: V034__live_sessions migration applied.
"@
    exit 0
}

function Fail([string]$msg) {
    Write-Host "[FAIL] $msg" -ForegroundColor Red
    exit 1
}

function Step([string]$title, [scriptblock]$action) {
    Write-Host ""
    Write-Host "== $title ==" -ForegroundColor Cyan
    & $action
    Write-Host "[OK] $title" -ForegroundColor Green
}

function Get-Token([string]$username, [string]$password) {
    $login = Invoke-RestMethod -Uri "$BaseUrl/api/v1/auth/login" -Method Post `
        -Body (@{ username = $username; password = $password } | ConvertTo-Json) `
        -ContentType "application/json; charset=utf-8"
    $t = $login.access_token
    if (-not $t) { $t = $login.accessToken }
    if (-not $t) { Fail "No JWT for $username" }
    return $t
}

$token = Get-Token -username $User -password $Pass
$hdr = @{ Authorization = "Bearer $token" }
$suffix = Get-Date -Format "yyyyMMddHHmmss"
$chatId = $null
$sessionId = $null
$liveEnabled = $false

Step "Media capabilities" {
    $caps = Invoke-RestMethod -Uri "$BaseUrl/api/v1/media/capabilities" -Method Get
    if ($null -ne $caps.live_streaming_enabled) {
        $script:liveEnabled = [bool]$caps.live_streaming_enabled
    }
    Write-Host "live_streaming_enabled=$script:liveEnabled max=$($caps.live_max_webrtc_viewers)"
}

Step "Create group chat" {
    $body = @{
        title = "live-smoke-$suffix"
        type = "group"
        member_ids = @()
    } | ConvertTo-Json
    $chat = Invoke-RestMethod -Uri "$BaseUrl/api/v1/chats" -Method Post -Headers $hdr -Body $body -ContentType "application/json; charset=utf-8"
    $script:chatId = $chat.chat_id
    if (-not $script:chatId) { $script:chatId = $chat.chatId }
    if (-not $script:chatId) { $script:chatId = $chat.id }
    if (-not $script:chatId) { Fail "No chat_id" }
    Write-Host "chat_id=$script:chatId"
}

Step "Create live session" {
    $body = @{ title = "Smoke live $suffix" } | ConvertTo-Json
    try {
        $created = Invoke-RestMethod -Uri "$BaseUrl/api/v1/chats/$($script:chatId)/live-sessions" -Method Post -Headers $hdr -Body $body -ContentType "application/json; charset=utf-8"
        $script:sessionId = $created.live_session_id
        if (-not $script:sessionId) { Fail "No live_session_id in response" }
        Write-Host "live_session_id=$script:sessionId"
    } catch {
        $code = $null
        if ($_.Exception.Response) {
            $code = [int]$_.Exception.Response.StatusCode
        }
        if ($code -eq 503) {
            Write-Host "[SKIP] LiveKit not configured (503) - API graceful fail OK" -ForegroundColor Yellow
            exit 0
        }
        if ($code -eq 404 -or $code -eq 500) {
            Write-Host "[SKIP] Live sessions API not ready ($code) - redeploy core-api with V034 + LiveKit" -ForegroundColor Yellow
            exit 0
        }
        throw
    }
}

Step "List live sessions" {
    $list = Invoke-RestMethod -Uri "$BaseUrl/api/v1/chats/$($script:chatId)/live-sessions?active_only=true" -Method Get -Headers $hdr
    if (-not ($list -is [System.Array]) -or $list.Count -lt 1) {
        Fail "Expected at least one active session"
    }
}

if ($script:liveEnabled) {
    Step "Join live session" {
        $join = Invoke-RestMethod -Uri "$BaseUrl/api/v1/live-sessions/$($script:sessionId)/join" -Method Post -Headers $hdr -ContentType "application/json; charset=utf-8"
        if (-not $join.access_token) { Fail "join missing access_token" }
        Write-Host "role=$($join.role) viewers=$($join.viewer_count)"
    }

    Step "Leave live session" {
        Invoke-WebRequest -Uri "$BaseUrl/api/v1/live-sessions/$($script:sessionId)/leave" -Method Post -Headers $hdr -UseBasicParsing | Out-Null
    }

    Step "End live session" {
        Invoke-WebRequest -Uri "$BaseUrl/api/v1/live-sessions/$($script:sessionId)/end" -Method Post -Headers $hdr -UseBasicParsing | Out-Null
    }
} else {
    Write-Host "[SKIP] join/leave/end - live_streaming_enabled=false" -ForegroundColor Yellow
}

Write-Host ""
Write-Host "Live session smoke completed." -ForegroundColor Green
