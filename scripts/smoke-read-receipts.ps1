# Smoke: per-message read receipts (requires live stack)
param(
    [string]$BaseUrl = "http://127.0.0.1:18080/api",
    [string]$User = "csadmin",
    [string]$Pass = "csadmin",
    [string]$Token = $env:SMOKE_ACCESS_TOKEN
)

$ErrorActionPreference = "Stop"
$apiRoot = $BaseUrl.TrimEnd('/')
if ($apiRoot -notmatch '/api$') { $apiRoot = "$apiRoot/api" }

if (-not $Token) {
    $login = Invoke-RestMethod -Uri "$($apiRoot -replace '/api$','')/api/v1/auth/login" -Method Post `
        -Body (@{ username = $User; password = $Pass } | ConvertTo-Json) `
        -ContentType "application/json; charset=utf-8"
    $Token = if ($login.access_token) { $login.access_token } else { $login.accessToken }
}
if (-not $Token) { throw "No access token (login failed)" }

$headers = @{ Authorization = "Bearer $Token"; "Content-Type" = "application/json" }

$chatBody = @{
    type = "group"
    title = "read-receipts-smoke-$([Guid]::NewGuid().ToString('N').Substring(0, 8))"
    member_ids = @()
} | ConvertTo-Json
$chat = Invoke-RestMethod -Uri "$apiRoot/v1/chats" -Headers $headers -Method POST -Body $chatBody
$chatId = $chat.id
if (-not $chatId) { throw "create chat returned no id" }

$messageBody = @{ type = "text"; content = "read receipts smoke" } | ConvertTo-Json
$message = Invoke-RestMethod -Uri "$apiRoot/v1/chats/$chatId/messages" -Headers $headers -Method POST -Body $messageBody
$messageId = $message.id
if (-not $messageId) { throw "send message returned no id" }

$batch = @{ message_ids = @($messageId) } | ConvertTo-Json
Invoke-WebRequest -Uri "$apiRoot/v1/chats/$chatId/read-batch" -Headers $headers -Method POST -Body $batch | Out-Null
Write-Host "POST /read-batch -> OK"

$rr = Invoke-RestMethod -Uri "$apiRoot/v1/chats/$chatId/read-receipts?message_id=$messageId" -Headers $headers -Method GET
if (-not $rr.message_id) { throw "GET read-receipts missing message_id" }
Write-Host "GET /read-receipts -> OK (read_by count: $($rr.read_by.Count))"

Write-Host "smoke-read-receipts: PASS"
