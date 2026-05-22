# Admin: GET /api/v1/admin/export/jobs (global list with optional filters).
param(
    [string]$BaseUrl = "http://localhost:8080",
    [string]$AdminUser = "csadmin",
    [string]$AdminPass = "csadmin",
    [string]$ChatId = "",
    [string]$Status = "",
    [int]$Limit = 10
)
$ErrorActionPreference = "Stop"
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path

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

$hdr = @{ Authorization = "Bearer $(Get-Token -User $AdminUser -Pass $AdminPass)" }
$uri = "$BaseUrl/api/v1/admin/export/jobs?limit=$Limit"
if ($ChatId) { $uri += "&chat_id=$([uri]::EscapeDataString($ChatId))" }
if ($Status) { $uri += "&status=$([uri]::EscapeDataString($Status))" }

Write-Host "GET $uri" -ForegroundColor Cyan
$list = Invoke-RestMethod -Uri $uri -Headers $hdr -Method Get
if ($null -eq $list.jobs) { throw "Missing jobs array in response" }
Write-Host "[OK] job_count=$($list.job_count) filter=$($list.status_filter) chat=$($list.chat_id_filter)" -ForegroundColor Green
if ($list.jobs.Count -gt 0 -and -not $list.jobs[0].chat_id) {
    Write-Host "[WARN] first job has no chat_id" -ForegroundColor Yellow
}
