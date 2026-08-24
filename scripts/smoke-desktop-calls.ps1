#Requires -Version 5.1
param(
    [string] $BaseUrl = 'http://127.0.0.1:18080',
    [string] $WebUrl = 'http://127.0.0.1:19088',
    [string] $Username = $(if ($env:KORUS_DESKTOP_SMOKE_USER) { $env:KORUS_DESKTOP_SMOKE_USER } else { 'admin' }),
    [string] $Password = $(if ($env:KORUS_DESKTOP_SMOKE_PASSWORD) { $env:KORUS_DESKTOP_SMOKE_PASSWORD } else { 'admin' })
)
$ErrorActionPreference = 'Stop'

$loginBody = @{ username = $Username; password = $Password } | ConvertTo-Json
$login = Invoke-RestMethod -Uri "$BaseUrl/api/v1/auth/login" -Method POST -Body $loginBody -ContentType 'application/json'
$h = @{ Authorization = "Bearer $($login.access_token)" }

$chats = Invoke-RestMethod -Uri "$BaseUrl/api/v1/chats" -Headers $h
$cid = $chats[0].id
if (-not $cid) { $cid = $chats[0].chat_id }

$callBody = @{ kind = 'group'; media_intent = 'audio' } | ConvertTo-Json
$call = Invoke-RestMethod -Uri "$BaseUrl/api/v1/chats/$cid/calls" -Method POST -Headers $h -Body $callBody -ContentType 'application/json'
$sessionId = $call.session_id
if (-not $sessionId) { Write-Error 'call session id missing' }

$joinUrl = "$WebUrl/?chat=$([uri]::EscapeDataString($cid))&call_session=$([uri]::EscapeDataString($sessionId))&call_mode=audio"
$web = Invoke-WebRequest -Uri $joinUrl -UseBasicParsing -TimeoutSec 15
if ($web.StatusCode -ge 400) { Write-Error "call join URL HTTP $($web.StatusCode)" }

Write-Host "PASS smoke-desktop-calls (neutral session=$sessionId join=$joinUrl)"
exit 0
