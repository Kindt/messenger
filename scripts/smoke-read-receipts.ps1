# Smoke: per-message read receipts (requires live stack)
param(
    [string]$BaseUrl = "http://127.0.0.1:9080/api",
    [string]$Token = $env:SMOKE_ACCESS_TOKEN
)

$ErrorActionPreference = "Stop"
if (-not $Token) {
    Write-Error "Set SMOKE_ACCESS_TOKEN (Bearer access token for a test user)."
}

$headers = @{ Authorization = "Bearer $Token"; "Content-Type" = "application/json" }

$chats = Invoke-RestMethod -Uri "$BaseUrl/v1/chats" -Headers $headers -Method GET
if (-not $chats -or $chats.Count -eq 0) {
    Write-Host "SKIP: no chats for user"
    exit 0
}
$chatId = $chats[0].id
$msgs = Invoke-RestMethod -Uri "$BaseUrl/v1/chats/$chatId/messages?limit=5" -Headers $headers -Method GET
if (-not $msgs -or $msgs.Count -eq 0) {
    Write-Host "SKIP: no messages in chat $chatId"
    exit 0
}
$messageId = $msgs[0].id

Invoke-WebRequest -Uri "$BaseUrl/v1/chats/$chatId/messages/$messageId/read" -Headers $headers -Method POST | Out-Null
Write-Host "POST /messages/{id}/read -> OK"

$batch = @{ message_ids = @($messageId) } | ConvertTo-Json
Invoke-WebRequest -Uri "$BaseUrl/v1/chats/$chatId/read-batch" -Headers $headers -Method POST -Body $batch | Out-Null
Write-Host "POST /read-batch -> OK"

$rr = Invoke-RestMethod -Uri "$BaseUrl/v1/chats/$chatId/read-receipts?message_id=$messageId" -Headers $headers -Method GET
if (-not $rr.message_id) { throw "GET read-receipts missing message_id" }
Write-Host "GET /read-receipts -> OK (read_by count: $($rr.read_by.Count))"

Write-Host "smoke-read-receipts: PASS"
