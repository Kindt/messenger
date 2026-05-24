# Spec 002 API-level parity smoke (T010 + T016 backend paths).
# WS/protocol checks: scripts/smoke-web-parity-ws.ps1 (T022).
# Optional browser DOM/RTC gates: specs/002-web-client-server-parity/HANDOFF.mdparam(
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
    Invoke-WebRequest -Uri "$BaseUrl/api/v1/chats/$chatId/messages/$msgId/pin" -Method Post -Headers $hdr -UseBasicParsing | Out-Null
    $pins = Invoke-RestMethod -Uri "$BaseUrl/api/v1/chats/$chatId/messages/pins" -Headers $hdr -Method Get
    if (-not $pins -or $pins.Count -lt 1) { Fail "pins list empty after pin" }
    Invoke-WebRequest -Uri "$BaseUrl/api/v1/chats/$chatId/messages/$msgId/pin" -Method Delete -Headers $hdr -UseBasicParsing | Out-Null
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
    Step "T016: file upload, download, public link" {
        $bytes = [System.Text.Encoding]::UTF8.GetBytes("parity-file-smoke $(Get-Date -Format o)")
        $uploadHeaders = @{}
        foreach ($k in $hdr.Keys) { $uploadHeaders[$k] = $hdr[$k] }
        $uploadHeaders["Content-Type"] = "application/octet-stream"
        $uploadHeaders["X-Filename"] = "parity.txt"
        $upload = Invoke-RestMethod -Uri "$BaseUrl/api/v1/files/upload" -Method Post -Headers $uploadHeaders -Body $bytes
        $fileId = $upload.file_id
        if (-not $fileId) { $fileId = $upload.id }
        if (-not $fileId) { Fail "upload returned no file_id" }
        $dl = Invoke-WebRequest -Uri "$BaseUrl/api/v1/files/$fileId/download" -Headers $hdr -UseBasicParsing
        if ($dl.StatusCode -ne 200) { Fail "download status $($dl.StatusCode)" }
        $link = Invoke-RestMethod -Uri "$BaseUrl/api/v1/files/$fileId/public-links" -Method Post -Headers $hdr `
            -Body (@{ link_kind = "A"; ttl_seconds = 3600 } | ConvertTo-Json) `
            -ContentType "application/json; charset=utf-8"
        if (-not $link.link_id) { $linkId = $link.id } else { $linkId = $link.link_id }
        if (-not $linkId) { Fail "public link create returned no id" }
        $links = Invoke-RestMethod -Uri "$BaseUrl/api/v1/files/$fileId/public-links" -Headers $hdr -Method Get
        if (-not $links) { Fail "public links list empty" }
        Invoke-WebRequest -Uri "$BaseUrl/api/v1/files/$fileId/public-links/$linkId" -Method Delete -Headers $hdr -UseBasicParsing | Out-Null
    }

    Step "T016: export request and status (API)" {
        & (Join-Path $scriptDir "smoke-export-chat.ps1") -BaseUrl $BaseUrl -ChatId $chatId -SkipDownload
        if ($LASTEXITCODE -ne $null -and $LASTEXITCODE -ne 0) {
            Fail "smoke-export-chat.ps1 failed"
        }
    }
}

Write-Host ""
Write-Host "[OK] web parity API smoke (spec 002 T010/T016 backend)" -ForegroundColor Green
Write-Host "Optional operator gates: browser DOM/RTC per specs/002-web-client-server-parity/HANDOFF.md" -ForegroundColor DarkGray
