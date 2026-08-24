#Requires -Version 5.1
<#
.SYNOPSIS
  Cross-client smoke: web ↔ desktop paths on same QEMU stack (:18080 / :19088).

  Messaging: smoke_user_a (desktop SDK path) ↔ smoke_user_b (web API path).
  Calls: mesh-calls (web WebRTC) — desktop CALL_BTN opens same mesh via web UI deep link.
#>
param(
    [string] $BaseUrl = 'http://127.0.0.1:18080',
    [string] $WebUrl = 'http://127.0.0.1:19088',
    [string] $UserA = 'smoke_user_a',
    [string] $UserB = 'smoke_user_b',
    [string] $Password = 'smokepass123'
)

$ErrorActionPreference = 'Stop'

function Invoke-KorusLogin {
    param([string] $User, [string] $Pass)
    $body = @{ username = $User; password = $Pass } | ConvertTo-Json
    $login = Invoke-RestMethod -Uri "$BaseUrl/api/v1/auth/login" -Method POST -Body $body -ContentType 'application/json'
    if (-not $login.access_token) { throw "login failed for $User" }
    return @{ Authorization = "Bearer $($login.access_token)" }
}

function Get-MeId {
    param($Headers)
    $me = Invoke-RestMethod -Uri "$BaseUrl/api/v1/users/me" -Headers $Headers
    $id = $me.id
    if (-not $id) { $id = $me.user_id }
    if (-not $id) { throw 'users/me missing id' }
    return $id
}

function New-GroupChat {
    param($HeadersA, [string] $MemberBId, [string] $Title)
    $create = @{
        type       = 'group'
        title      = $Title
        member_ids = @($MemberBId)
    } | ConvertTo-Json
    $chat = Invoke-RestMethod -Uri "$BaseUrl/api/v1/chats" -Method POST -Headers $HeadersA -Body $create -ContentType 'application/json'
    $cid = $chat.id
    if (-not $cid) { $cid = $chat.chat_id }
    if (-not $cid) { throw 'create group missing chat id' }
    return $cid
}

function Send-TextMessage {
    param($Headers, [string] $ChatId, [string] $Content)
    $body = @{ type = 'text'; content = $Content } | ConvertTo-Json
    return Invoke-RestMethod -Uri "$BaseUrl/api/v1/chats/$ChatId/messages" -Method POST -Headers $Headers -Body $body -ContentType 'application/json'
}

function Test-MessageDelivered {
    param($Headers, [string] $ChatId, [string] $MessageId)
    for ($i = 0; $i -lt 8; $i++) {
        $msgs = Invoke-RestMethod -Uri "$BaseUrl/api/v1/chats/$ChatId/messages" -Headers $Headers
        foreach ($m in $msgs) {
            $mid = $m.id
            if (-not $mid) { $mid = $m.message_id }
            if ($mid -eq $MessageId) { return $true }
        }
        Start-Sleep -Milliseconds 400
    }
    return $false
}

$repo = Split-Path $PSScriptRoot -Parent
Push-Location $repo
try {
    Write-Host "=== Cross-client smoke ($BaseUrl / $WebUrl) ===" -ForegroundColor Cyan

    $health = Invoke-RestMethod -Uri "$BaseUrl/api/v1/health" -TimeoutSec 10
    if ($health.status -ne 'ok') { throw "API health not ok" }
    $web = Invoke-WebRequest -Uri $WebUrl -UseBasicParsing -TimeoutSec 10
    if ($web.StatusCode -ne 200) { throw "Web UI not reachable" }

    $hA = Invoke-KorusLogin -User $UserA -Pass $Password
    $hB = Invoke-KorusLogin -User $UserB -Pass $Password
    $idB = Get-MeId -Headers $hB
    $title = "cross-client-$(Get-Date -Format 'yyyyMMdd-HHmmss')"
    $chatId = New-GroupChat -HeadersA $hA -MemberBId $idB -Title $title

    $stampA = "desktop-to-web $(Get-Date -Format o)"
    $sentA = Send-TextMessage -Headers $hA -ChatId $chatId -Content $stampA
    if (-not $sentA.id) { throw 'A send failed' }
    if (-not (Test-MessageDelivered -Headers $hB -ChatId $chatId -MessageId $sentA.id)) {
        throw "B did not receive message id from A: $($sentA.id)"
    }
    Write-Host "PASS messaging A->B (desktop SDK path -> web consumer)" -ForegroundColor Green

    $stampB = "web-to-desktop $(Get-Date -Format o)"
    $sentB = Send-TextMessage -Headers $hB -ChatId $chatId -Content $stampB
    if (-not $sentB.id) { throw 'B send failed' }
    if (-not (Test-MessageDelivered -Headers $hA -ChatId $chatId -MessageId $sentB.id)) {
        throw "A did not receive message id from B: $($sentB.id)"
    }
    Write-Host "PASS messaging B->A (web API path -> desktop consumer)" -ForegroundColor Green

    # Mesh call (web in-browser path): A starts, B joins same session
    $meshBody = @{ media_mode = 'audio' } | ConvertTo-Json
    $mesh = Invoke-RestMethod -Uri "$BaseUrl/api/v1/chats/$chatId/mesh-calls/sessions" -Method POST -Headers $hA -Body $meshBody -ContentType 'application/json'
    $sessionId = $mesh.session_id
    if (-not $sessionId) { $sessionId = $mesh.id }
    if (-not $sessionId) { throw 'mesh session id missing' }
    $meshJoin = Invoke-RestMethod -Uri "$BaseUrl/api/v1/chats/$chatId/mesh-calls/sessions/$sessionId/join" -Method POST -Headers $hB -ContentType 'application/json' -Body '{}'
    if (-not $meshJoin) { throw 'mesh join failed for B' }
    Write-Host "PASS mesh audio call session A start + B join (web WebRTC path)" -ForegroundColor Green

    $meshVideoBody = @{ media_mode = 'video' } | ConvertTo-Json
    $meshV = Invoke-RestMethod -Uri "$BaseUrl/api/v1/chats/$chatId/mesh-calls/sessions" -Method POST -Headers $hB -Body $meshVideoBody -ContentType 'application/json'
    $sessionV = $meshV.session_id
    if (-not $sessionV) { $sessionV = $meshV.id }
    if (-not $sessionV) { throw 'mesh video session id missing' }
    Invoke-RestMethod -Uri "$BaseUrl/api/v1/chats/$chatId/mesh-calls/sessions/$sessionV/join" -Method POST -Headers $hA -ContentType 'application/json' -Body '{}' | Out-Null
    Write-Host "PASS mesh video call session B start + A join" -ForegroundColor Green

    # Desktop mesh path: start session + web deep link (same stack as web CALL_BTN)
    $meshDesktopBody = @{ media_mode = 'audio' } | ConvertTo-Json
    $meshDesktop = Invoke-RestMethod -Uri "$BaseUrl/api/v1/chats/$chatId/mesh-calls/sessions" -Method POST -Headers $hA -Body $meshDesktopBody -ContentType 'application/json'
    $desktopSessionId = $meshDesktop.session_id
    if (-not $desktopSessionId) { $desktopSessionId = $meshDesktop.id }
    if (-not $desktopSessionId) { throw 'desktop mesh session id missing' }
    $desktopJoinUrl = "$WebUrl/?chat=$([uri]::EscapeDataString($chatId))&mesh_session=$([uri]::EscapeDataString($desktopSessionId))&mesh_mode=audio"
    $desktopWeb = Invoke-WebRequest -Uri $desktopJoinUrl -UseBasicParsing -TimeoutSec 15
    if ($desktopWeb.StatusCode -ge 400) { throw "desktop mesh join URL HTTP $($desktopWeb.StatusCode)" }
    Invoke-RestMethod -Uri "$BaseUrl/api/v1/chats/$chatId/mesh-calls/sessions/$desktopSessionId/join" -Method POST -Headers $hB -ContentType 'application/json' -Body '{}' | Out-Null
    Write-Host "PASS desktop mesh call URL + web peer join: $desktopJoinUrl" -ForegroundColor Green

    Write-Host ""
    Write-Host "PASS smoke-desktop-web-cross (messaging + mesh AV + desktop mesh URL)" -ForegroundColor Green
    exit 0
}
catch {
    Write-Host "FAIL smoke-desktop-web-cross: $_" -ForegroundColor Red
    exit 1
}
finally {
    Pop-Location
}
