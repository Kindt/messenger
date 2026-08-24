# Dot-source: mobile client API smokes (spec 032).

$script:KorusMobileTokenCache = @{}

function Invoke-KorusMobileFail {
    param([string]$Message)
    Write-Host "[FAIL] $Message" -ForegroundColor Red
    exit 1
}

function Test-KorusMobileHealth {
    param([string]$BaseUrl = "http://127.0.0.1:18080")
    try {
        $h = Invoke-RestMethod -Uri "$BaseUrl/api/v1/health" -TimeoutSec 15
        if (-not $h.status) { return $false }
        return $true
    } catch {
        return $false
    }
}

function Get-KorusMobileToken {
    param(
        [string]$BaseUrl,
        [string]$User = "user1",
        [string]$Pass = "12345"
    )
    $key = "$BaseUrl|$User"
    if ($script:KorusMobileTokenCache.ContainsKey($key)) {
        return $script:KorusMobileTokenCache[$key]
    }
    . (Join-Path $PSScriptRoot "SmokeApi.ps1")
    $token = Get-KorusApiToken -BaseUrl $BaseUrl -User $User -Pass $Pass
    $script:KorusMobileTokenCache[$key] = $token
    return $token
}

function Invoke-KorusMobileSdkTests {
    param([string]$TestFilter = "")
    $root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
    $gradlew = Join-Path $root "gradlew.bat"
    $args = @(":mobile:mobile-client-sdk:jvmTest", "--no-daemon", "--no-configuration-cache")
    if ($TestFilter) {
        $args += @("--tests", $TestFilter)
    }
    & $gradlew @args
    if ($LASTEXITCODE -ne 0) {
        Invoke-KorusMobileFail "mobile-client-sdk tests failed"
    }
    Write-Host "[OK] mobile-client-sdk tests" -ForegroundColor Green
}

function Invoke-KorusMobileMessagingApiSmoke {
    param(
        [string]$BaseUrl = "http://127.0.0.1:18080",
        [string]$User = "user1",
        [string]$Pass = "12345"
    )
    $token = Get-KorusMobileToken -BaseUrl $BaseUrl -User $User -Pass $Pass
    $hdr = @{ Authorization = "Bearer $token" }

    $chats = Invoke-RestMethod -Uri "$BaseUrl/api/v1/chats" -Headers $hdr -Method Get
    $chatId = $null
    if ($chats -and @($chats).Count -gt 0) {
        $chatId = $chats[0].id
        if (-not $chatId) { $chatId = $chats[0].chat_id }
    }
    if (-not $chatId) {
        $contacts = Invoke-RestMethod -Uri "$BaseUrl/api/v1/contacts" -Headers $hdr -Method Get
        $memberId = $null
        if ($contacts -and @($contacts).Count -gt 0) {
            $memberId = $contacts[0].user_id
            if (-not $memberId) { $memberId = $contacts[0].id }
        }
        if (-not $memberId) {
            Invoke-KorusMobileFail "no chats and no contacts for messaging smoke"
        }
        $title = "mobile-smoke-" + (Get-Date -Format 'yyyyMMdd-HHmmss')
        $chat = Invoke-RestMethod -Uri "$BaseUrl/api/v1/chats" -Method Post -Headers $hdr `
            -Body (@{ type = "group"; title = $title; member_ids = @($memberId) } | ConvertTo-Json) `
            -ContentType "application/json; charset=utf-8"
        $chatId = $chat.id
        if (-not $chatId) { $chatId = $chat.chat_id }
    }
    if (-not $chatId) { Invoke-KorusMobileFail "no chat id" }

    $msg = Invoke-RestMethod -Uri "$BaseUrl/api/v1/chats/$chatId/messages" -Method Post -Headers $hdr `
        -Body (@{ type = "text"; content = "mobile-smoke-msg" } | ConvertTo-Json) `
        -ContentType "application/json; charset=utf-8"
    if (-not $msg.id) { Invoke-KorusMobileFail "send message failed" }

    $list = Invoke-RestMethod -Uri "$BaseUrl/api/v1/chats/$chatId/messages?limit=5" -Headers $hdr -Method Get
    if (-not $list) { Invoke-KorusMobileFail "list messages empty" }
    Write-Host ('[OK] messaging API smoke chat=' + $chatId) -ForegroundColor Green
}
