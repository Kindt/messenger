# export-compliance-prep with include_file=true (server-side upload + file message).
param(
    [string]$ChatId = "",
    [string]$BaseUrl = "http://localhost:8080",
    [int]$TextMessageCount = 2,
    [switch]$SkipPrep
)
$ErrorActionPreference = "Stop"
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path

if ($SkipPrep -and -not $ChatId) {
    throw "ChatId required with -SkipPrep"
}

$prepArgs = @{
    BaseUrl       = $BaseUrl
    MessageCount  = [Math]::Max(1, $TextMessageCount)
    IncludeFile   = $true
}
if ($ChatId) {
    $prepArgs["ChatId"] = $ChatId
    $prepArgs["NoCreateGroup"] = $true
}

$prepOut = @()
if (-not $SkipPrep) {
    $prepOut = @(& "$scriptDir\smoke-admin-export-compliance-prep.ps1" @prepArgs)
    $chatLine = $prepOut | Where-Object { $_ -match '^CHAT_ID=' } | Select-Object -Last 1
    if ($chatLine) {
        $ChatId = ($chatLine -replace '^CHAT_ID=', '').Trim()
    }
    if (-not $ChatId) {
        $ChatId = ($prepOut | Select-Object -First 1).ToString().Trim()
    }
    if (-not $ChatId) { throw "prep did not return chat id" }
    Write-Host "Waiting 2s (retention SELECT age buffer) ..." -ForegroundColor DarkGray
    Start-Sleep -Seconds 2
}

Write-Host "[OK] chat with attachment seeded (via prep API)" -ForegroundColor Green
Write-Output $ChatId
$fileLine = $prepOut | Where-Object { $_ -match '^FILE_ID=' } | Select-Object -Last 1
if ($fileLine) { Write-Output $fileLine }
