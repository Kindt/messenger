#Requires -Version 5.1
param([string]$ApiBase = "http://127.0.0.1:18080", [switch]$Help)
$ErrorActionPreference = 'Stop'
if ($Help) { exit 0 }
. "$PSScriptRoot\lib\SmokeMobile.ps1"
Invoke-KorusMobileSdkTests -TestFilter "AttachmentPathResolverTest"
. "$PSScriptRoot\lib\SmokeMobile.ps1"
$token = Get-KorusMobileToken -BaseUrl $ApiBase
$cap = Invoke-RestMethod -Uri "$ApiBase/api/v1/platform/capabilities" -Headers @{ Authorization = "Bearer $token" }
if (-not $cap) { throw "capabilities missing" }
Write-Host ('[PASS] smoke-mobile-files') -ForegroundColor Green
