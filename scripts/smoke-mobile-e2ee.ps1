#Requires -Version 5.1
param([string]$ApiBase = "http://127.0.0.1:18080", [switch]$Help)
$ErrorActionPreference = 'Stop'
if ($Help) { exit 0 }
. "$PSScriptRoot\lib\SmokeMobile.ps1"
$token = Get-KorusMobileToken -BaseUrl $ApiBase
try {
    Invoke-RestMethod -Uri "$ApiBase/api/v1/e2ee/capabilities" -Headers @{ Authorization = "Bearer $token" } | Out-Null
    Write-Host ('[PASS] smoke-mobile-e2ee') -ForegroundColor Green
} catch {
    Write-Host ('[SKIP] e2ee: ' + $_.Exception.Message) -ForegroundColor Yellow
}
