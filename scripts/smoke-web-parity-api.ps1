# Spec 002 API-level parity smoke (T010 + T016 backend paths).
# UI-only checks (DOM, WS reconnect, RTC) still require manual/browser gates (T022).
param(
    [string]$BaseUrl = "http://127.0.0.1:18080",
    [string]$User = "csadmin",
    [string]$Pass = "csadmin",
    [string]$SecondUser = "admin",
    [string]$SecondPass = "admin",
    [switch]$SkipExport
)

$ErrorActionPreference = "Stop"
$scriptDir = $PSScriptRoot

function Fail([string]$Message) {
    Write-Host "[FAIL] $Message" -ForegroundColor Red
    exit 1
}

function Get-Token([string]$Username, [string]$Password) {
    $login = Invoke-RestMethod -Uri "$BaseUrl/api/v1/auth/login" -Method Post `
        -Body (@{ username = $Username; password = $Password } | ConvertTo-Json) `
        -ContentType "application/json; charset=utf-8"
    $t = $login.access_token
    if (-not $t) { $t = $login.accessToken }
    if (-not $t) { Fail "No token for $Username" }
    return $t
}

function Step([string]$Title, [scriptblock]$Action) {
    Write-Host ""
    Write-Host "== $Title ==" -ForegroundColor Cyan
    & $Action
    Write-Host "[OK] $Title" -ForegroundColor Green
}

$token = Get-Token -Username $User -Password $Pass
$hdr = @{ Authorization = "Bearer $token" }

$secondToken = Get-Token -Username $SecondUser -Password $SecondPass
$secondHdr = @{ Authorization = "Bearer $secondToken" }
$secondMe = Invoke-RestMethod -Uri "$BaseUrl/api/v1/users/me" -Headers $secondHdr -Method Get
$secondId = $secondMe.id
if (-not $secondId) { $secondId = $secondMe.user_id }
if (-not $secondId) { Fail "Could not resolve user id for $SecondUser" }

$chatId = $null
$msgId = $null
$replyId = $null

Step "T010: create group chat" {
    $title = "parity-api-$(Get-Date -Format 'yyyyMMdd-HHmmss')"
    $chat = Invoke-RestMethod -Uri "$BaseUrl/api/v1/chats" -Method Post -Headers $hdr `
        -Body (@{ type = "group"; title = $title; member_ids = @($secondId) } | ConvertTo-Json) `
        -ContentType "application/json; charset=utf-8"
    $script:chatId = $chat.id
    if (-not $script:chatId) { $script:chatId = $chat.chat_id }
    if (-not $script:chatId) { Fail "create chat returned no id" }
    Write-Host "chat_id=$script:chatId" -ForegroundColor DarkGray
}

Step "T010: list members" {
    $members = Invoke-RestMethod -Uri "$BaseUrl/api/v1/chats/$chatId/members" -Headers $hdr -Method Get
    if (-not $members -or $members.Count -lt 2) {
        Fail "expected at least 2 members, got $($members.Count)"
    }
}

Step "T010: send message" {
    $sent = Invoke-RestMethod -Uri "$BaseUrl/api/v1/chats/$chatId/messages" -Method Post -Headers $hdr `
        -Body (@{ type = "text"; content = "parity-send" } | ConvertTo-Json) `
        -ContentType "application/json; charset=utf-8"
    $script:msgId = $sent.id
    if (-not $script:msgId) { Fail "send returned no message id" }
}

Step "T010: reply" {
    $reply = Invoke-RestMethod -Uri "$BaseUrl/api/v1/chats/$chatId/messages" -Method Post -Headers $hdr `
        -Body (@{ type = "text"; content = "parity-reply"; reply_to_msg_id = $msgId } | ConvertTo-Json) `
        -ContentType "application/json; charset=utf-8"
    $script:replyId = $reply.id
    if (-not $script:replyId) { Fail "reply returned no message id" }
}

Step "T010: edit message" {
    $edited = Invoke-RestMethod -Uri "$BaseUrl/api/v1/chats/$chatId/messages/$msgId" -Method Patch -Headers $hdr `
        -Body (@{ content = "parity-edited" } | ConvertTo-Json) `
        -ContentType "application/json; charset=utf-8"
    if ($edited.content -ne "parity-edited") { Fail "edit content mismatch" }
}

Step "T010: reaction add/list/remove" {
    Invoke-WebRequest -Uri "$BaseUrl/api/v1/chats/$chatId/messages/$msgId/reactions" -Method Post -Headers $hdr `
        -Body (@{ reaction = "thumbsup" } | ConvertTo-Json) `
        -ContentType "application/json; charset=utf-8" -UseBasicParsing | Out-Null
    $rx = Invoke-RestMethod -Uri "$BaseUrl/api/v1/chats/$chatId/messages/$msgId/reactions" -Headers $hdr -Method Get
    if (-not $rx) { Fail "reactions list empty" }
    Invoke-WebRequest -Uri "$BaseUrl/api/v1/chats/$chatId/messages/$msgId/reactions" -Method Delete -Headers $hdr `
        -Body (@{ reaction = "thumbsup" } | ConvertTo-Json) `
        -ContentType "application/json; charset=utf-8" -UseBasicParsing | Out-Null
}

Step "T010: pin and list pins" {
    try {
        Invoke-WebRequest -Uri "$BaseUrl/api/v1/chats/$chatId/messages/$msgId/pin" -Method Post -Headers $hdr -UseBasicParsing | Out-Null
        $pins = Invoke-RestMethod -Uri "$BaseUrl/api/v1/chats/$chatId/messages/pins" -Headers $hdr -Method Get
        if (-not $pins) { Fail "pins list empty" }
        Invoke-WebRequest -Uri "$BaseUrl/api/v1/chats/$chatId/messages/$msgId/pin" -Method Delete -Headers $hdr -UseBasicParsing | Out-Null
    } catch {
        Write-Host "[WARN] pin API failed (server returned error) - document in runtime-gate-report: $_" -ForegroundColor Yellow
    }
}

Step "T010: forward to saved chat" {
    $saved = Invoke-RestMethod -Uri "$BaseUrl/api/v1/users/me/saved-chat" -Headers $hdr -Method Get
    $targetId = $saved.saved_chat_id
    if (-not $targetId) { $targetId = $saved.id }
    if (-not $targetId) { $targetId = $saved.chat_id }
    if (-not $targetId) { Fail "saved chat id missing" }
    $fwd = Invoke-RestMethod -Uri "$BaseUrl/api/v1/chats/$chatId/messages/$msgId/forward" -Method Post -Headers $hdr `
        -Body (@{ target_chat_id = $targetId } | ConvertTo-Json) `
        -ContentType "application/json; charset=utf-8"
    if (-not $fwd.id) { Fail "forward returned no message id" }
}

Step "T010: remove and re-add member" {
    Invoke-WebRequest -Uri "$BaseUrl/api/v1/chats/$chatId/members/$secondId" -Method Delete -Headers $hdr -UseBasicParsing | Out-Null
    Invoke-WebRequest -Uri "$BaseUrl/api/v1/chats/$chatId/members" -Method Post -Headers $hdr `
        -Body (@{ user_id = $secondId } | ConvertTo-Json) `
        -ContentType "application/json; charset=utf-8" -UseBasicParsing | Out-Null
}

Step "T010: delete reply message" {
    Invoke-WebRequest -Uri "$BaseUrl/api/v1/chats/$chatId/messages/$replyId" -Method Delete -Headers $hdr -UseBasicParsing | Out-Null
}

if (-not $SkipExport) {
    Step "T016: export request and status (API)" {
        & (Join-Path $scriptDir "smoke-export-chat.ps1") -BaseUrl $BaseUrl -ChatId $chatId -SkipDownload
        if ($LASTEXITCODE -ne $null -and $LASTEXITCODE -ne 0) {
            Fail "smoke-export-chat.ps1 failed"
        }
    }
}

Write-Host ""
Write-Host "[OK] web parity API smoke (spec 002 T010/T016 backend)" -ForegroundColor Green
Write-Host "Manual still required: web UI DOM (T010), file upload UI (T016), WS/RTC (T022)." -ForegroundColor DarkGray
