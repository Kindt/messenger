# Seed non-empty chat messages for retention hot-body candidate SELECT (smoke / dev).
param(
    [string]$ChatId = "",
    [string]$BaseUrl = "http://localhost:8080",
    [string]$User = "csadmin",
    [string]$Pass = "csadmin",
    [int]$MessageCount = 3,
    [switch]$CreateGroup,
    [switch]$PrepareRetention,
    [switch]$IncludeFile,
    [string]$FileName = "compliance-smoke.txt",
    [int]$AgeBufferSeconds = 2
)
$ErrorActionPreference = "Stop"
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
. (Join-Path $scriptDir "lib\SmokeApi.ps1")

$token = Get-KorusApiToken -BaseUrl $BaseUrl -User $User -Pass $Pass
$hdr = New-KorusAuthHeaders -Token $token

if ($PrepareRetention) {
    if (-not $ChatId -and -not $CreateGroup) {
        Write-Host "GET chats ..." -ForegroundColor Cyan
        $chats = Invoke-RestMethod -Uri "$BaseUrl/api/v1/chats" -Headers $hdr
        if (-not $chats -or @($chats).Count -eq 0) {
            throw "No chats; pass -ChatId or -CreateGroup"
        }
        $first = @($chats)[0]
        $ChatId = $first.id
        if (-not $ChatId) { $ChatId = $first.chat_id }
        Write-Host "Using chat $ChatId" -ForegroundColor DarkGray
    }
    Write-Host "POST export-compliance-prep ..." -ForegroundColor Cyan
    $prepBody = @{
        message_count = $MessageCount
        create_group  = $CreateGroup -and -not $ChatId
    }
    if ($ChatId) { $prepBody.chat_id = $ChatId }
    if ($IncludeFile) {
        $prepBody.include_file = $true
        if ($FileName) { $prepBody.file_name = $FileName }
    }
    $prep = Invoke-RestMethod -Uri "$BaseUrl/api/v1/admin/export-compliance-prep" -Method Post -Headers $hdr `
        -ContentType "application/json" -Body ($prepBody | ConvertTo-Json)
    $ChatId = $prep.chat_id
    if (-not $ChatId) { $ChatId = $prep.chatId }
    if (-not $ChatId) { throw "prep did not return chat_id" }
    $ids = $prep.message_ids
    if (-not $ids) { $ids = $prep.messageIds }
    $fid = $prep.file_id
    if (-not $fid) { $fid = $prep.fileId }
    Write-Host "[OK] prep chat=$ChatId messages=$($ids.Count) retention=$($prep.retention_patched) file_id=$fid" -ForegroundColor Green
} else {
    if (-not $ChatId) {
        if ($CreateGroup) {
            Write-Host "POST group chat ..." -ForegroundColor Cyan
            $chat = Invoke-RestMethod -Uri "$BaseUrl/api/v1/chats" -Method Post -Headers $hdr `
                -ContentType "application/json" `
                -Body (@{ type = "group"; title = "retention-smoke"; member_ids = @() } | ConvertTo-Json)
            $ChatId = $chat.id
            if (-not $ChatId) { $ChatId = $chat.chat_id }
            if (-not $ChatId) { throw "No chat id from create" }
            Write-Host "[OK] created chat $ChatId" -ForegroundColor Green
        } else {
            Write-Host "GET chats ..." -ForegroundColor Cyan
            $chats = Invoke-RestMethod -Uri "$BaseUrl/api/v1/chats" -Headers $hdr
            if (-not $chats -or @($chats).Count -eq 0) {
                throw "No chats; pass -ChatId or -CreateGroup"
            }
            $first = @($chats)[0]
            $ChatId = $first.id
            if (-not $ChatId) { $ChatId = $first.chat_id }
            Write-Host "Using chat $ChatId" -ForegroundColor DarkGray
        }
    }

    for ($i = 1; $i -le $MessageCount; $i++) {
        $body = @{
            type    = "text"
            content = "retention-smoke seed $i at $(Get-Date -Format o)"
        } | ConvertTo-Json
        $msg = Invoke-RestMethod -Uri "$BaseUrl/api/v1/chats/$ChatId/messages" -Method Post -Headers $hdr `
            -ContentType "application/json" -Body $body
        $mid = $msg.id
        if (-not $mid) { $mid = $msg.message_id }
        Write-Host "  message $i id=$mid" -ForegroundColor DarkGray
    }
}

if ($AgeBufferSeconds -gt 0) {
    Write-Host "Waiting ${AgeBufferSeconds}s (created_at < now for retention SELECT) ..." -ForegroundColor DarkGray
    Start-Sleep -Seconds $AgeBufferSeconds
}

Write-Host "[OK] seeded $MessageCount message(s) in chat $ChatId" -ForegroundColor Green
Write-Output $ChatId
