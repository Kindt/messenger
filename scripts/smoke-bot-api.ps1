# Bot API REST smoke (spec 009 T203): register, subscribe, sendMessage.
param(
    [string]$BaseUrl = "http://127.0.0.1:18080",
    [string]$User = "csadmin",
    [string]$Pass = "csadmin",
    [switch]$Help
)

$ErrorActionPreference = "Stop"
if ($Help) {
    Write-Host @"
Usage: .\scripts\smoke-bot-api.ps1 [-BaseUrl http://127.0.0.1:18080]

Flow: login -> POST /bots -> create group -> subscribe -> POST /bot/send -> verify message.
Prereq: core-api with V032__bots migration; bot added to chat as member on subscribe.
"@
    exit 0
}

function Fail([string]$msg) {
    Write-Host "[FAIL] $msg" -ForegroundColor Red
    exit 1
}

function Step([string]$title, [scriptblock]$action) {
    Write-Host ""
    Write-Host "== $title ==" -ForegroundColor Cyan
    & $action
    Write-Host "[OK] $title" -ForegroundColor Green
}

function Get-Token([string]$username, [string]$password) {
    $login = Invoke-RestMethod -Uri "$BaseUrl/api/v1/auth/login" -Method Post `
        -Body (@{ username = $username; password = $password } | ConvertTo-Json) `
        -ContentType "application/json; charset=utf-8"
    $t = $login.access_token
    if (-not $t) { $t = $login.accessToken }
    if (-not $t) { Fail "No JWT for $username" }
    return $t
}

$token = Get-Token -username $User -password $Pass
$hdr = @{ Authorization = "Bearer $token" }
$suffix = Get-Date -Format "yyyyMMddHHmmss"
$botName = "smoke_bot_$suffix"
$webhook = "https://example.com/korus-bot-smoke/$suffix"
$botToken = $null
$botId = $null
$chatId = $null
$msgContent = "bot-smoke-$suffix"

Step "Create bot" {
    $body = @{
        bot_name = $botName
        display_name = "Smoke Bot $suffix"
        listen_mode = "READ_ALL"
        default_webhook_url = $webhook
    } | ConvertTo-Json
    try {
        $created = Invoke-RestMethod -Uri "$BaseUrl/api/v1/bots" -Method Post -Headers $hdr -Body $body -ContentType "application/json; charset=utf-8"
    } catch {
        Fail "POST /bots failed (is V032 migrated?): $($_.Exception.Message)"
    }
    $script:botId = $created.bot_id
    $script:botToken = $created.access_token
    if (-not $script:botId -or -not $script:botToken) { Fail "bot_id or access_token missing in create response" }
    if (-not $script:botToken.StartsWith("kbt_")) { Fail "access_token must start with kbt_" }
}

Step "List owned bots" {
    $list = Invoke-RestMethod -Uri "$BaseUrl/api/v1/bots" -Method Get -Headers $hdr
    if ($list.Count -lt 1) { Fail "GET /bots returned empty list" }
}

Step "Create group chat" {
    $title = "bot-smoke-$suffix"
    $body = @{ type = "group"; title = $title; member_ids = @() } | ConvertTo-Json
    $chat = Invoke-RestMethod -Uri "$BaseUrl/api/v1/chats" -Method Post -Headers $hdr -Body $body -ContentType "application/json; charset=utf-8"
    $script:chatId = $chat.id
    if (-not $script:chatId) { $script:chatId = $chat.chat_id }
    if (-not $script:chatId) { Fail "chat id missing" }
}

Step "Subscribe bot to chat" {
    $uri = "$BaseUrl/api/v1/bots/$script:botId/chats/$script:chatId/subscribe"
    Invoke-RestMethod -Uri $uri -Method Post -Headers $hdr -Body "{}" -ContentType "application/json; charset=utf-8" | Out-Null
}

Step "Send message as bot" {
    $body = @{
        chat_id = $script:chatId
        type = "text"
        content = $msgContent
        client_msg_id = "bot-smoke-$suffix"
    } | ConvertTo-Json
    $botHdr = @{ Authorization = "Bearer $script:botToken" }
    $msg = Invoke-RestMethod -Uri "$BaseUrl/api/v1/bot/send" -Method Post -Headers $botHdr -Body $body -ContentType "application/json; charset=utf-8"
    $script:sentMsgId = $msg.id
    if (-not $script:sentMsgId) { $script:sentMsgId = $msg.message_id }
    if (-not $script:sentMsgId) { Fail "bot send response missing message id" }
}

Step "Verify bot message in chat history" {
    $found = $false
    for ($i = 0; $i -lt 15; $i++) {
        $msgs = @(Invoke-RestMethod -Uri "$BaseUrl/api/v1/chats/$script:chatId/messages?limit=20" -Method Get -Headers $hdr)
        foreach ($m in $msgs) {
            $mid = $m.id
            if (-not $mid) { $mid = $m.message_id }
            if ($mid -eq $script:sentMsgId) { $found = $true; break }
        }
        if ($found) { break }
        Start-Sleep -Seconds 1
    }
    if (-not $found) { Fail "bot message id not found in chat history" }
}

Write-Host ""
Write-Host "[OK] smoke-bot-api ($botName -> chat $script:chatId)" -ForegroundColor Green
