#Requires -Version 5.1
param([string]$ApiBase = "http://127.0.0.1:18080", [switch]$Help)
$ErrorActionPreference = 'Stop'
if ($Help) { exit 0 }
. "$PSScriptRoot\lib\SmokeMobile.ps1"
if (-not (Test-KorusMobileHealth -BaseUrl $ApiBase)) { Invoke-KorusMobileFail "health fail" }
$token = Get-KorusMobileToken -BaseUrl $ApiBase
$me = Invoke-RestMethod -Uri "$ApiBase/api/v1/users/me" -Headers @{ Authorization = "Bearer $token" }
Write-Host ('[PASS] smoke-mobile-auth me=' + $me.login) -ForegroundColor Green
