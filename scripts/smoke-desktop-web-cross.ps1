#Requires -Version 5.1
<#
.SYNOPSIS
  Cross-client smoke: web ↔ desktop paths on same QEMU stack (:18080 / :19088).

  Messaging: smoke_user_a (desktop SDK path) ↔ smoke_user_b (web API path).
  Calls: provider-neutral /calls — desktop starts in-process PCMU; web deep link remains for share.
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

    # Provider-neutral call: A starts, B joins the same session.
    $audioBody = @{ kind = 'group'; media_intent = 'audio' } | ConvertTo-Json
    $audio = Invoke-RestMethod -Uri "$BaseUrl/api/v1/chats/$chatId/calls" -Method POST -Headers $hA -Body $audioBody -ContentType 'application/json'
    $sessionId = $audio.session_id
    if (-not $sessionId) { throw 'audio call session id missing' }
    $audioJoin = Invoke-RestMethod -Uri "$BaseUrl/api/v1/chats/$chatId/calls/$sessionId/join" -Method POST -Headers $hB -ContentType 'application/json' -Body '{}'
    if (-not $audioJoin.participant_id) { throw 'audio call join failed for B' }
    Write-Host "PASS neutral audio call session A start + B join" -ForegroundColor Green

    Invoke-RestMethod -Uri "$BaseUrl/api/v1/chats/$chatId/calls/$sessionId/end" -Method POST -Headers $hA -ContentType 'application/json' -Body '{}' | Out-Null
    $videoBody = @{ kind = 'group'; media_intent = 'video' } | ConvertTo-Json
    $video = Invoke-RestMethod -Uri "$BaseUrl/api/v1/chats/$chatId/calls" -Method POST -Headers $hB -Body $videoBody -ContentType 'application/json'
    $sessionV = $video.session_id
    if (-not $sessionV) { throw 'video call session id missing' }
    Invoke-RestMethod -Uri "$BaseUrl/api/v1/chats/$chatId/calls/$sessionV/join" -Method POST -Headers $hA -ContentType 'application/json' -Body '{}' | Out-Null
    Write-Host "PASS neutral video call session B start + A join" -ForegroundColor Green

    Invoke-RestMethod -Uri "$BaseUrl/api/v1/chats/$chatId/calls/$sessionV/end" -Method POST -Headers $hB -ContentType 'application/json' -Body '{}' | Out-Null

    # Desktop handoff: neutral session + provider-neutral web deep link.
    $desktopBody = @{ kind = 'group'; media_intent = 'audio' } | ConvertTo-Json
    $desktop = Invoke-RestMethod -Uri "$BaseUrl/api/v1/chats/$chatId/calls" -Method POST -Headers $hA -Body $desktopBody -ContentType 'application/json'
    $desktopSessionId = $desktop.session_id
    if (-not $desktopSessionId) { throw 'desktop call session id missing' }
    $desktopJoinUrl = "$WebUrl/?chat=$([uri]::EscapeDataString($chatId))&call_session=$([uri]::EscapeDataString($desktopSessionId))&call_mode=audio"
    $desktopWeb = Invoke-WebRequest -Uri $desktopJoinUrl -UseBasicParsing -TimeoutSec 15
    if ($desktopWeb.StatusCode -ge 400) { throw "desktop call join URL HTTP $($desktopWeb.StatusCode)" }
    Invoke-RestMethod -Uri "$BaseUrl/api/v1/chats/$chatId/calls/$desktopSessionId/join" -Method POST -Headers $hB -ContentType 'application/json' -Body '{}' | Out-Null
    Write-Host "PASS desktop neutral call URL + web join: $desktopJoinUrl" -ForegroundColor Green

    Write-Host ""
    Write-Host "PASS smoke-desktop-web-cross (messaging + neutral AV + desktop call URL)" -ForegroundColor Green
    exit 0
}
catch {
    Write-Host "FAIL smoke-desktop-web-cross: $_" -ForegroundColor Red
    exit 1
}
finally {
    Pop-Location
}
