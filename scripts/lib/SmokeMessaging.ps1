# Dot-source: REST helpers for messaging smokes (spec 003). Windows mirror of SmokeMessaging.sh.

function Invoke-SmokeFail {
    param([string]$Message)
    Write-Host "[FAIL] $Message" -ForegroundColor Red
    exit 1
}

function Invoke-SmokeStep {
    param([string]$Title)
    Write-Host ""
    Write-Host "== $Title ==" -ForegroundColor Cyan
}

function Get-SmokeApiToken {
    param(
        [string]$BaseUrl,
        [string]$User,
        [string]$Pass
    )
    $login = Invoke-RestMethod -Uri "$BaseUrl/api/v1/auth/login" -Method Post `
        -Body (@{ username = $User; password = $Pass } | ConvertTo-Json) `
        -ContentType "application/json; charset=utf-8"
    $t = $login.access_token
    if (-not $t) { $t = $login.accessToken }
    if (-not $t) { Invoke-SmokeFail "No token for $User" }
    return $t
}

function Register-SmokeUser {
    param(
        [string]$BaseUrl,
        [string]$User,
        [string]$Pass,
        [string]$DisplayName
    )
    try {
        $r = Invoke-WebRequest -Uri "$BaseUrl/api/v1/auth/register" -Method Post `
            -Body (@{ username = $User; password = $Pass; display_name = $DisplayName } | ConvertTo-Json) `
            -ContentType "application/json; charset=utf-8" -UseBasicParsing
        if ($r.StatusCode -ge 200 -and $r.StatusCode -lt 300) { return $true }
    } catch {
        if ($_.Exception.Response.StatusCode.value__ -eq 409) { return $true }
        throw
    }
    return $false
}

function Get-SmokeUserId {
    param([string]$BaseUrl, [string]$Token)
    $me = Invoke-RestMethod -Uri "$BaseUrl/api/v1/users/me" -Headers @{ Authorization = "Bearer $Token" } -Method Get
    $id = $me.id
    if (-not $id) { $id = $me.user_id }
    if (-not $id) { Invoke-SmokeFail "users/me id missing" }
    return $id
}

function New-SmokeP2pChat {
    param([string]$BaseUrl, [string]$Token, [string]$MemberId)
    $chat = Invoke-RestMethod -Uri "$BaseUrl/api/v1/chats" -Method Post -Headers @{ Authorization = "Bearer $Token" } `
        -Body (@{ type = "p2p"; member_ids = @($MemberId) } | ConvertTo-Json) `
        -ContentType "application/json; charset=utf-8"
    $id = $chat.id
    if (-not $id) { $id = $chat.chat_id }
    if (-not $id) { Invoke-SmokeFail "p2p chat id missing" }
    return $id
}

function New-SmokeGroupChat {
    param([string]$BaseUrl, [string]$Token, [string]$Title, [string[]]$MemberIds)
    $chat = Invoke-RestMethod -Uri "$BaseUrl/api/v1/chats" -Method Post -Headers @{ Authorization = "Bearer $Token" } `
        -Body (@{ type = "group"; title = $Title; member_ids = $MemberIds } | ConvertTo-Json) `
        -ContentType "application/json; charset=utf-8"
    $id = $chat.id
    if (-not $id) { $id = $chat.chat_id }
    if (-not $id) { Invoke-SmokeFail "group chat id missing" }
    return $id
}

function Send-SmokeMessage {
    param(
        [string]$BaseUrl,
        [string]$Token,
        [string]$ChatId,
        [string]$Content,
        [string]$ReplyToMsgId = ""
    )
    $body = @{ type = "text"; content = $Content }
    if ($ReplyToMsgId) { $body.reply_to_msg_id = $ReplyToMsgId }
    $sent = Invoke-RestMethod -Uri "$BaseUrl/api/v1/chats/$ChatId/messages" -Method Post `
        -Headers @{ Authorization = "Bearer $Token" } `
        -Body ($body | ConvertTo-Json) -ContentType "application/json; charset=utf-8"
    $mid = $sent.id
    if (-not $mid) { Invoke-SmokeFail "send returned no message id" }
    return $mid
}

function Test-SmokeMessagesContain {
    param([string]$BaseUrl, [string]$Token, [string]$ChatId, [string]$Needle)
    $msgs = Invoke-RestMethod -Uri "$BaseUrl/api/v1/chats/$ChatId/messages?limit=50" `
        -Headers @{ Authorization = "Bearer $Token" } -Method Get
    foreach ($m in $msgs) {
        if ($m.content -and $m.content.Contains($Needle)) { return $true }
    }
    return $false
}

function Wait-SmokeMessage {
    param(
        [string]$BaseUrl,
        [string]$Token,
        [string]$ChatId,
        [string]$Needle,
        [int]$TimeoutSec = 15
    )
    for ($i = 0; $i -lt $TimeoutSec; $i++) {
        if (Test-SmokeMessagesContain -BaseUrl $BaseUrl -Token $Token -ChatId $ChatId -Needle $Needle) {
            return $true
        }
        Start-Sleep -Seconds 1
    }
    return $false
}
