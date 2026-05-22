# PATCH chat retention so hot-body janitor can select candidates (smoke / dev).
param(
    [Parameter(Mandatory = $true)]
    [string]$ChatId,
    [string]$BaseUrl = "http://localhost:8080",
    [string]$AdminUser = "csadmin",
    [string]$AdminPass = "csadmin",
    [int]$HotBodyMaxAgeDays = 0
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

$hdr = @{ Authorization = "Bearer $(Get-Token -User $AdminUser -Pass $AdminPass)" }
$uri = "$BaseUrl/api/v1/admin/chats/$ChatId/retention"
$body = @{
    hot_message_body_max_age_days = $HotBodyMaxAgeDays
    hot_metadata_min_age_days     = $null
    archive_metadata_enabled      = $false
    deep_archive_enabled          = $true
    legal_hold                    = $false
} | ConvertTo-Json

Write-Host "PATCH $uri (hot_body=$HotBodyMaxAgeDays, deep_archive=true) ..." -ForegroundColor Cyan
$policy = Invoke-RestMethod -Uri $uri -Method Patch -Headers $hdr -ContentType "application/json" -Body $body
Write-Host "[OK] chat retention prepared; eff hot_body=$($policy.hot_message_body_max_age_days)" -ForegroundColor Green
Write-Host "Ensure chat has non-empty messages older than policy (send a few, wait, or use existing history)." -ForegroundColor DarkGray
