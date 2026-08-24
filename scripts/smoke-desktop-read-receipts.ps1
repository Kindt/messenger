#Requires -Version 5.1
param(
    [string] $BaseUrl = 'http://127.0.0.1:18080',
    [string] $Username = $(if ($env:KORUS_DESKTOP_SMOKE_USER) { $env:KORUS_DESKTOP_SMOKE_USER } else { 'admin' }),
    [string] $Password = $(if ($env:KORUS_DESKTOP_SMOKE_PASSWORD) { $env:KORUS_DESKTOP_SMOKE_PASSWORD } else { 'admin' })
)
$ErrorActionPreference = 'Stop'

$login = Invoke-RestMethod -Uri "$BaseUrl/api/v1/auth/login" -Method POST `
    -Body (@{ username = $Username; password = $Password } | ConvertTo-Json) -ContentType 'application/json'
$h = @{ Authorization = "Bearer $($login.access_token)" }

$chats = Invoke-RestMethod -Uri "$BaseUrl/api/v1/chats" -Headers $h
$cid = $chats[0].id
if (-not $cid) { $cid = $chats[0].chat_id }

Invoke-RestMethod -Uri "$BaseUrl/api/v1/chats/$cid/read" -Method POST -Headers $h -Body '{}' -ContentType 'application/json' | Out-Null
Invoke-RestMethod -Uri "$BaseUrl/api/v1/chats/$cid/typing" -Method POST -Headers $h -Body '{}' -ContentType 'application/json' | Out-Null
$unread = Invoke-RestMethod -Uri "$BaseUrl/api/v1/chats/$cid/unread-count" -Headers $h
if ($null -eq $unread.unread_count) { Write-Error 'unread-count missing' }

Write-Host "PASS smoke-desktop-read-receipts (chat=$cid unread=$($unread.unread_count))"
exit 0
