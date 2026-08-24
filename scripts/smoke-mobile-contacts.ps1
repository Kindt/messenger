#Requires -Version 5.1
param([string]$ApiBase = "http://127.0.0.1:18080", [switch]$Help)
$ErrorActionPreference = 'Stop'
if ($Help) { exit 0 }
. "$PSScriptRoot\lib\SmokeMobile.ps1"
$token = Get-KorusMobileToken -BaseUrl $ApiBase
$c = Invoke-RestMethod -Uri "$ApiBase/api/v1/contacts" -Headers @{ Authorization = "Bearer $token" } -Method Get
if ($null -eq $c) { throw "contacts null" }
Write-Host ('[PASS] smoke-mobile-contacts count=' + @($c).Count) -ForegroundColor Green
