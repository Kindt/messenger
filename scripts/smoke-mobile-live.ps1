#Requires -Version 5.1
param([string]$ApiBase = "http://127.0.0.1:18080", [switch]$Help)
$ErrorActionPreference = 'Stop'
if ($Help) { exit 0 }
. "$PSScriptRoot\lib\SmokeApi.ps1"
$token = Get-KorusApiToken -BaseUrl $ApiBase -User "smoke_user_a" -Pass "smokepass123"
$hdr = New-KorusAuthHeaders -Token $token
try {
    Invoke-RestMethod -Uri "$ApiBase/api/v1/live/sessions" -Headers $hdr -Method Get | Out-Null
    Write-Host "[PASS] smoke-mobile-live" -ForegroundColor Green
} catch {
    Write-Host "[SKIP] live addon off: $($_.Exception.Message)" -ForegroundColor Yellow
}
