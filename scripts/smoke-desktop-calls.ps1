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

$meshBody = @{ media_mode = 'audio' } | ConvertTo-Json
$mesh = Invoke-RestMethod -Uri "$BaseUrl/api/v1/chats/$cid/mesh-calls/sessions" -Method POST -Headers $h -Body $meshBody -ContentType 'application/json'
$sessionId = $mesh.session_id
if (-not $sessionId) { $sessionId = $mesh.id }
if (-not $sessionId) { Write-Error 'mesh session id missing' }

$joinUrl = "$WebUrl/?chat=$([uri]::EscapeDataString($cid))&mesh_session=$([uri]::EscapeDataString($sessionId))&mesh_mode=audio"
$web = Invoke-WebRequest -Uri $joinUrl -UseBasicParsing -TimeoutSec 15
if ($web.StatusCode -ge 400) { Write-Error "mesh join URL HTTP $($web.StatusCode)" }

Write-Host "PASS smoke-desktop-calls (mesh session=$sessionId join=$joinUrl)"
exit 0
