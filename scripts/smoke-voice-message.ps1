# Smoke voice message API (spec 022). Host :18080 or guest :8080.
param(
    [string]$BaseUrl = "http://127.0.0.1:18080",
    [string]$User = "",
    [string]$Pass = "",
    [switch]$Help
)

$ErrorActionPreference = "Stop"
if ($Help) {
    Write-Host "Usage: .\scripts\smoke-voice-message.ps1 [-BaseUrl url] [-User u] [-Pass p]"
    exit 0
}

if (-not $User) { $User = $env:SMOKE_USER }
if (-not $User) { $User = "smoke_user_a" }
if (-not $Pass) { $Pass = $env:SMOKE_PASS }
if (-not $Pass) { $Pass = "smokepass123" }

function Fail([string]$m) { Write-Host "[FAIL] $m" -ForegroundColor Red; exit 1 }

$api = "$BaseUrl/api/v1"
$login = Invoke-RestMethod -Uri "$api/auth/login" -Method Post `
    -Body (@{ username = $User; password = $Pass } | ConvertTo-Json) `
    -ContentType "application/json; charset=utf-8"
$token = if ($login.access_token) { $login.access_token } else { $login.accessToken }
if (-not $token) { Fail "login failed for $User" }

$headers = @{ Authorization = "Bearer $token" }
$chats = Invoke-RestMethod -Uri "$api/chats" -Headers $headers -Method Get
if (-not $chats -or $chats.Count -lt 1) { Fail "no chats for $User" }
$chatId = $chats[0].id
if (-not $chatId) { $chatId = $chats[0].chat_id }
if (-not $chatId) { Fail "chat id missing" }

$bytes = [System.Text.Encoding]::UTF8.GetBytes("voice-smoke")
$uploadHeaders = @{ Authorization = "Bearer $token"; "Content-Type" = "application/octet-stream"; "X-Filename" = "voice.webm" }
$up = Invoke-RestMethod -Uri "$api/files/upload" -Method Post -Headers $uploadHeaders -Body $bytes
$fileId = $up.id
if (-not $fileId) { $fileId = $up.file_id }
if (-not $fileId) { Fail "upload returned no file id" }

$msg = Invoke-RestMethod -Uri "$api/chats/$chatId/messages" -Method Post -Headers $headers `
    -Body (@{ type = "voice"; content = $fileId; duration_ms = 1200 } | ConvertTo-Json) `
    -ContentType "application/json; charset=utf-8"
if ($msg.type -ne "voice" -and $msg.type -ne "e2ee-voice") { Fail "expected type=voice got $($msg.type)" }

Write-Host "[OK] voice message smoke chat=$chatId file=$fileId" -ForegroundColor Green
