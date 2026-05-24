# GDPR export completeness smoke (epic 03).
param(
    [string]$BaseUrl = "http://127.0.0.1:18080",
    [string]$User = "csadmin",
    [string]$Pass = "csadmin"
)
$ErrorActionPreference = "Stop"
$scriptDir = $PSScriptRoot

& (Join-Path $scriptDir "smoke-web-parity-api.ps1") -BaseUrl $BaseUrl -User $User -Pass $Pass
if ($LASTEXITCODE -ne $null -and $LASTEXITCODE -ne 0) { exit 1 }

Write-Host "[OK] export GDPR fulfillment smoke (API paths + export via parity script)" -ForegroundColor Green
