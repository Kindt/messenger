# Export-replay before hot-row purge: seed chat, export (export_v1), verify completeness gate.
param(
    [string]$BaseUrl = "http://localhost:8080",
    [string]$ChatId = "",
    [int]$PollSeconds = 120
)
$ErrorActionPreference = "Stop"
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
. "$scriptDir\lib\SmokeMessaging.ps1"

if (-not $ChatId) {
    $prepOut = & "$scriptDir\smoke-admin-export-compliance-prep.ps1" -BaseUrl $BaseUrl
    $line = $prepOut | Where-Object { $_ -match '^CHAT_ID=' } | Select-Object -Last 1
    if ($line) { $ChatId = ($line -replace '^CHAT_ID=', '').Trim() }
    if (-not $ChatId) { throw "Could not resolve chat id from compliance prep" }
}

Write-Host "Export-replay before purge: chat=$ChatId" -ForegroundColor Cyan
& "$scriptDir\smoke-export-chat.ps1" -BaseUrl $BaseUrl -ChatId $ChatId -PollSeconds $PollSeconds
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Write-Host "Purge gate now requires export_v1 (stub_written rejected)." -ForegroundColor Green
Write-Host "Run retention worker with EXPORT_REQUIRED_BEFORE_PURGE=true and RETENTION_HOT_ROW_PURGE_ENABLED=true on live stack." -ForegroundColor DarkGray
Write-Host "See scripts/smoke-retention-purge.ps1 for purge status check." -ForegroundColor DarkGray
exit 0
