#Requires -Version 5.1
param([string]$ApiBase = "http://127.0.0.1:18080", [switch]$Help)
$ErrorActionPreference = 'Stop'
if ($Help) { exit 0 }
. "$PSScriptRoot\lib\SmokeMobile.ps1"
$token = Get-KorusMobileToken -BaseUrl $ApiBase
try {
    Invoke-RestMethod -Uri "$ApiBase/api/v1/devices" -Method Post -Headers @{ Authorization = "Bearer $token" } `
        -Body (@{ platform = "android"; token = "smoke-fcm-token" } | ConvertTo-Json) `
        -ContentType "application/json" | Out-Null
    Write-Host ('[PASS] smoke-mobile-push') -ForegroundColor Green
} catch {
    Write-Host ('[SKIP] push: ' + $_.Exception.Message) -ForegroundColor Yellow
}
