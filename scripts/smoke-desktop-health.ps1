#Requires -Version 5.1
<#
.SYNOPSIS
  Smoke: QEMU API health for desktop client integration.
#>
param(
    [string] $BaseUrl = 'http://127.0.0.1:18080'
)

$ErrorActionPreference = 'Stop'
$uri = "$BaseUrl/api/v1/health"
Write-Host "GET $uri"
$r = Invoke-WebRequest -Uri $uri -UseBasicParsing -TimeoutSec 15
if ($r.StatusCode -ne 200) {
    Write-Error "Health failed: $($r.StatusCode)"
}
Write-Host "PASS smoke-desktop-health ($($r.StatusCode))"
exit 0
