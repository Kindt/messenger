#Requires -Version 5.1
param([string]$ApiBase = "http://127.0.0.1:18080", [switch]$Help)
$ErrorActionPreference = 'Stop'
if ($Help) { exit 0 }
. "$PSScriptRoot\lib\SmokeMobile.ps1"
$token = Get-KorusMobileToken -BaseUrl $ApiBase
$cap = Invoke-RestMethod -Uri "$ApiBase/api/v1/platform/capabilities" -Headers @{ Authorization = "Bearer $token" }
$calls = @($cap.capabilities) -contains "mesh_webrtc" -or @($cap.capabilities) -contains "jitsi_conference"
if (-not $calls) { Write-Host '[SKIP] calls capabilities off' -ForegroundColor Yellow; exit 0 }
Write-Host ('[PASS] smoke-mobile-calls') -ForegroundColor Green
