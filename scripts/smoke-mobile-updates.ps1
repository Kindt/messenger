#Requires -Version 5.1
param([string]$ApiBase = "http://127.0.0.1:18080", [switch]$Help)
$ErrorActionPreference = 'Stop'
if ($Help) { exit 0 }
. "$PSScriptRoot\lib\SmokeMobile.ps1"
Invoke-KorusMobileSdkTests -TestFilter "AttachmentPathResolverTest"
Write-Host "[PASS] smoke-mobile-updates (SDK + manifest client in app)" -ForegroundColor Green
