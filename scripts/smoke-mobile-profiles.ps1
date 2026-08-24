#Requires -Version 5.1
param([string]$ApiBase = "http://127.0.0.1:18080", [switch]$Help)
$ErrorActionPreference = 'Stop'
if ($Help) { Write-Host "W1 profile store SDK test"; exit 0 }
. "$PSScriptRoot\lib\SmokeMobile.ps1"
Invoke-KorusMobileSdkTests -TestFilter "ProfileStoreTest"
Write-Host "[PASS] smoke-mobile-profiles" -ForegroundColor Green
