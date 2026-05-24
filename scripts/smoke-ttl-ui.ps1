# Smoke: send message with visibility_ttl_seconds=60 and verify API contract for web TTL UI.
# Full DOM check (msg-ttl-indicator) requires web UI; verified manually on QEMU 2026-05-24.
# Example: .\scripts\smoke-ttl-ui.ps1 -BaseUrl http://127.0.0.1:18080
param(
    [string]$BaseUrl = "http://localhost:8080",
    [string]$User = "csadmin",
    [string]$Pass = "csadmin",
    [string]$ChatId = "",
    [int]$TtlSeconds = 60
)
$ErrorActionPreference = "Stop"

$loginUri = "$BaseUrl/api/v1/auth/login"
Write-Host "POST $loginUri (user=$User)..." -ForegroundColor Cyan
$login = Invoke-RestMethod -Uri $loginUri -Method Post `
    -Body (@{ username = $User; password = $Pass } | ConvertTo-Json) `
    -ContentType "application/json; charset=utf-8"
$token = $login.access_token
if (-not $token) { $token = $login.accessToken }
if (-not $token) { throw "No access token from login" }

$headers = @{ Authorization = "Bearer $token" }

if (-not $ChatId) {
    Write-Host "GET $BaseUrl/api/v1/chats ..." -ForegroundColor Cyan
    $chats = Invoke-RestMethod -Uri "$BaseUrl/api/v1/chats" -Headers $headers -Method Get
    if (-not $chats -or $chats.Count -eq 0) {
        throw "No chats for user; pass -ChatId"
    }
    $ChatId = $chats[0].id
    if (-not $ChatId) { $ChatId = $chats[0].chat_id }
    Write-Host "Using first chat: $ChatId" -ForegroundColor DarkGray
}

$marker = "ttl-ui-smoke-$(Get-Date -Format 'yyyyMMdd-HHmmss')"
$postUri = "$BaseUrl/api/v1/chats/$ChatId/messages"
Write-Host "POST $postUri visibility_ttl_seconds=$TtlSeconds marker=$marker ..." -ForegroundColor Cyan
$sent = Invoke-RestMethod -Uri $postUri -Method Post -Headers $headers `
    -Body (@{ type = "text"; content = $marker; visibility_ttl_seconds = $TtlSeconds } | ConvertTo-Json) `
    -ContentType "application/json; charset=utf-8"

$msgId = $sent.id
$ttl = $sent.visibility_ttl_seconds
if ($null -eq $ttl) { $ttl = $sent.ttl_seconds }
if ($ttl -ne $TtlSeconds) {
    throw "Expected visibility_ttl_seconds=$TtlSeconds in response, got $ttl"
}

$listUri = "$BaseUrl/api/v1/chats/$ChatId/messages?limit=10"
Write-Host "GET $listUri (poll latest) ..." -ForegroundColor Cyan
$found = $false
for ($i = 0; $i -lt 5; $i++) {
    Start-Sleep -Seconds 1
    $msgs = Invoke-RestMethod -Uri $listUri -Headers $headers -Method Get
    foreach ($m in $msgs) {
        if ($m.id -eq $msgId -or $m.content -eq $marker) {
            $readTtl = $m.visibility_ttl_seconds
            if ($null -eq $readTtl) { $readTtl = $m.ttl_seconds }
            if ($readTtl -ne $TtlSeconds) {
                throw "Listed message TTL mismatch: expected $TtlSeconds, got $readTtl"
            }
            $found = $true
            break
        }
    }
    if ($found) { break }
}
if (-not $found) {
    throw "Sent message not found in chat history"
}

Write-Host "[OK] TTL message persisted: msg_id=$msgId ttl=$TtlSeconds marker=$marker" -ForegroundColor Green
Write-Host "Web UI: open chat $ChatId, find '$marker', expect meta '· ⏱ …' (.msg-ttl-indicator)" -ForegroundColor DarkGray
Write-Host "CHAT_ID=$ChatId"
Write-Host "MSG_ID=$msgId"
Write-Host "MARKER=$marker"
