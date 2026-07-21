param(
    [string]$RepoRoot,
    [string]$ProjectKey,
    [switch]$Help
)
$ErrorActionPreference = "Stop"
if ($Help) {
    Write-Host "Usage: .\deploy\sonar-qemu\list-new-issues.ps1 [-RepoRoot path] [-ProjectKey key]"
    exit 0
}
. (Join-Path $PSScriptRoot "config.ps1")
. (Join-Path $PSScriptRoot "lib\LabApi.ps1")
$auth = Get-SonarLabAuthHeaders
$keyEnc = Get-SonarLabProjectKeyEncoded -RepoRoot $RepoRoot -ProjectKey $ProjectKey

$uri = "$SonarQemuUrl/api/issues/search?componentKeys=$keyEnc&resolved=false&sinceLeakPeriod=true&ps=100"
$resp = Invoke-RestMethod -Uri $uri -Headers $auth
$resp.issues | Sort-Object rule, component, line | ForEach-Object {
    $file = $_.component -replace '^[^:]+:', ''
    Write-Host ("{0} {1}:{2} {3}" -f $_.rule, $file, $_.line, $_.message)
}
Write-Host ("total={0}" -f $resp.total)
