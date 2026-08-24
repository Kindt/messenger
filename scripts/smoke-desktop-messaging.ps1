#Requires -Version 5.1
param(
    [string] $BaseUrl = 'http://127.0.0.1:18080',
    [string] $Username = $(if ($env:KORUS_DESKTOP_SMOKE_USER) { $env:KORUS_DESKTOP_SMOKE_USER } else { 'admin' }),
    [string] $Password = $(if ($env:KORUS_DESKTOP_SMOKE_PASSWORD) { $env:KORUS_DESKTOP_SMOKE_PASSWORD } else { 'admin' })
)
$ErrorActionPreference = 'Stop'
& "$PSScriptRoot\smoke-desktop-auth.ps1" -BaseUrl $BaseUrl -Username $Username -Password $Password -SkipUi | Out-Null

$login = Invoke-RestMethod -Uri "$BaseUrl/api/v1/auth/login" -Method POST `
    -Body (@{ username = $Username; password = $Password } | ConvertTo-Json) -ContentType 'application/json'
$h = @{ Authorization = "Bearer $($login.access_token)" }

$chats = Invoke-RestMethod -Uri "$BaseUrl/api/v1/chats" -Headers $h
if (-not $chats -or $chats.Count -eq 0) { Write-Error 'No chats for messaging smoke' }
$cid = $chats[0].id
if (-not $cid) { $cid = $chats[0].chat_id }

$body = @{ type = 'text'; content = "desktop-smoke $(Get-Date -Format o)" } | ConvertTo-Json
$sent = Invoke-RestMethod -Uri "$BaseUrl/api/v1/chats/$cid/messages" -Method POST -Headers $h -Body $body -ContentType 'application/json'
if (-not $sent.id) { Write-Error 'send message failed' }

$msgs = Invoke-RestMethod -Uri "$BaseUrl/api/v1/chats/$cid/messages" -Headers $h
Write-Host "PASS smoke-desktop-messaging (chat=$cid sent=$($sent.id) messages=$($msgs.Count))"
exit 0
