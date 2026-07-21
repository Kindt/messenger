param(
    [string]$RepoRoot,
    [string]$ProjectKey,
    [switch]$Help
)
$ErrorActionPreference = "Stop"
if ($Help) {
    Write-Host "Usage: .\deploy\sonar-qemu\quality-status.ps1 [-RepoRoot path] [-ProjectKey key]"
    exit 0
}
. (Join-Path $PSScriptRoot "config.ps1")
. (Join-Path $PSScriptRoot "lib\LabApi.ps1")
$auth = Get-SonarLabAuthHeaders
$keyEnc = Get-SonarLabProjectKeyEncoded -RepoRoot $RepoRoot -ProjectKey $ProjectKey

$qg = Invoke-RestMethod -Uri "$SonarQemuUrl/api/qualitygates/project_status?projectKey=$keyEnc" -Headers $auth
Write-Host ("qualityGate={0}" -f $qg.projectStatus.status)
$qg.projectStatus.conditions | ForEach-Object {
    Write-Host ("{0} {1} actual={2} error={3}" -f $_.status, $_.metricKey, $_.actualValue, $_.errorThreshold)
}
$m = Invoke-RestMethod -Uri "$SonarQemuUrl/api/measures/component?component=$keyEnc&metricKeys=bugs,vulnerabilities,code_smells,security_hotspots" -Headers $auth
$m.component.measures | ForEach-Object { Write-Host ("{0}={1}" -f $_.metric, $_.value) }
