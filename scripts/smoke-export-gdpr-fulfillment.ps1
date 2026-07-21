# GDPR export completeness smoke (P1-6 / epic 03).
param(
    [string]$BaseUrl = "http://127.0.0.1:18080",
    [string]$User = "csadmin",
    [string]$Pass = "csadmin"
)
$ErrorActionPreference = "Stop"
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
. (Join-Path $scriptDir "lib\SmokeMessaging.ps1")

Write-Host "[gdpr-export] API $BaseUrl"
$token = Get-SmokeApiToken -BaseUrl $BaseUrl -User $User -Pass $Pass
$headers = @{ Authorization = "Bearer $token" }

$guide = Invoke-RestMethod -Uri "$BaseUrl/api/v1/admin/ui/export-compliance-guide" -Headers $headers
if (-not $guide.completeness_policy) {
    throw "export-compliance-guide: missing completeness_policy"
}
$fields = @($guide.completeness_policy.required_fields)
if ($fields.Count -lt 3) {
    throw "completeness_policy.required_fields too short: $($fields.Count)"
}
$strict = $guide.completeness_policy.strict
Write-Host "[OK] completeness policy: $($fields -join ', ') strict=$strict"

$required = @("messages", "chat", "gdpr_disclosures")
foreach ($name in $required) {
    if ($fields -notcontains $name) {
        throw "completeness_policy missing required field: $name"
    }
}

if (-not $guide.gdpr_disclosures_reference) {
    throw "export-compliance-guide: missing gdpr_disclosures_reference"
}
Write-Host "[OK] gdpr_disclosures_reference present"

. (Join-Path $scriptDir "lib\Reset-QemuLabOrgIpAllowlist.ps1")
Reset-QemuLabOrgIpAllowlist -BaseUrl $BaseUrl | Out-Null

& (Join-Path $scriptDir "smoke-web-parity-api.ps1") -BaseUrl $BaseUrl
if ($LASTEXITCODE -ne $null -and $LASTEXITCODE -ne 0) { exit 1 }

Write-Host "[OK] export GDPR fulfillment smoke" -ForegroundColor Green
