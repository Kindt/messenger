# End-to-end: admin export-suggest (local) -> audit -> optional poll/download via smoke-export-chat.

# Requires EXPORT_ADMIN_SUGGEST_ENABLED=true on core-api.

param(

    [Parameter(Mandatory = $true)]

    [string]$ChatId,

    [string]$BaseUrl = "http://localhost:8080",

    [string]$AdminUser = "csadmin",

    [string]$AdminPass = "csadmin",

    [string]$ChatUser = "csadmin",

    [string]$ChatPass = "csadmin",

    [string]$Dispatch = "local",

    [int]$PollSeconds = 120,

    [switch]$SkipExportPoll

)

$ErrorActionPreference = "Stop"



function Get-Token {

    param($User, $Pass)

    $login = Invoke-RestMethod -Uri "$BaseUrl/api/v1/auth/login" -Method Post `

        -Body (@{ username = $User; password = $Pass } | ConvertTo-Json) `

        -ContentType "application/json; charset=utf-8"

    $t = $login.access_token

    if (-not $t) { $t = $login.accessToken }

    if (-not $t) { throw "No token for $User" }

    return $t

}



Write-Host "Admin login..." -ForegroundColor Cyan

$adminHdr = @{ Authorization = "Bearer $(Get-Token -User $AdminUser -Pass $AdminPass)" }



Write-Host "POST export-suggest dispatch=$Dispatch chat=$ChatId ..." -ForegroundColor Cyan

try {

    $suggest = Invoke-RestMethod -Uri "$BaseUrl/api/v1/admin/chats/$ChatId/export-suggest" -Method Post `

        -Headers $adminHdr -ContentType "application/json" `

        -Body (@{

            dispatch = $Dispatch

            candidate_message_count = 2

            reason = "hot_body_candidates"

        } | ConvertTo-Json)

    Write-Host "[OK] suggest: dispatch=$($suggest.dispatch) auto_job=$($suggest.auto_queued_job_id)" -ForegroundColor Green

} catch {

    if ($_.Exception.Response.StatusCode.value__ -eq 404) {

        Write-Host "[FAIL] export-suggest disabled? Set EXPORT_ADMIN_SUGGEST_ENABLED=true" -ForegroundColor Red

    }

    throw

}



Start-Sleep -Seconds 1

$audit = Invoke-RestMethod -Uri "$BaseUrl/api/v1/admin/audit-events?action=export.suggested&resource_id=$ChatId&limit=5" -Headers $adminHdr

if (-not $audit -or @($audit).Count -eq 0) {

    throw "No export.suggested audit for chat $ChatId"

}

Write-Host "[OK] audit export.suggested rows: $(@($audit).Count)" -ForegroundColor Green



if ($SkipExportPoll) {

    Write-Host "Skipped export poll (-SkipExportPoll)" -ForegroundColor DarkGray

    exit 0

}



$jobId = $suggest.auto_queued_job_id

if (-not $jobId) { $jobId = $suggest.autoQueuedJobId }

if ($jobId) {

    $scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path

    Write-Host "Polling auto-queued job $jobId as $ChatUser ..." -ForegroundColor Cyan

    & "$scriptDir\smoke-export-chat.ps1" -BaseUrl $BaseUrl -User $ChatUser -Pass $ChatPass `

        -ChatId $ChatId -JobId $jobId -PollSeconds $PollSeconds

    exit $LASTEXITCODE

}



Write-Host "No auto_queued_job_id (set EXPORT_AUTO_QUEUE_ON_SUGGESTED=true). Run smoke-export-chat manually." -ForegroundColor Yellow

exit 0

