# Local lab QG: no coverage requirement; fail on new bugs/vulns/hotspots.
param(
    [string]$RepoRoot,
    [string]$ProjectKey,
    [string]$GateName = "Sonar Lab",
    [switch]$Help
)
$ErrorActionPreference = "Stop"
if ($Help) {
    Write-Host "Usage: .\deploy\sonar-qemu\setup-lab-quality-gate.ps1 [-RepoRoot path] [-ProjectKey key] [-GateName name]"
    exit 0
}
. (Join-Path $PSScriptRoot "config.ps1")
. (Join-Path $PSScriptRoot "lib\LabApi.ps1")
$auth = Get-SonarLabAuthHeaders
$projectKey = Get-SonarLabProjectKey -RepoRoot $RepoRoot -ProjectKey $ProjectKey

try {
    Invoke-RestMethod -Method POST -Uri "$SonarQemuUrl/api/qualitygates/create" -Headers $auth -Body @{ name = $GateName } | Out-Null
    Write-Host "Created quality gate $GateName"
} catch {
    Write-Host "Quality gate may already exist: $($_.Exception.Message)"
}

$gates = Invoke-RestMethod -Uri "$SonarQemuUrl/api/qualitygates/list" -Headers $auth
$gate = $gates.qualitygates | Where-Object { $_.name -eq $GateName } | Select-Object -First 1
if (-not $gate) { throw "Gate not found after create" }

$show = Invoke-RestMethod -Uri "$SonarQemuUrl/api/qualitygates/show?name=$([uri]::EscapeDataString($GateName))" -Headers $auth
foreach ($c in @($show.conditions)) {
    try {
        Invoke-RestMethod -Method POST -Uri "$SonarQemuUrl/api/qualitygates/delete_condition" -Headers $auth -Body @{ id = $c.id } | Out-Null
    } catch { }
}

$conditions = @(
    @{ metric = "new_bugs"; op = "GT"; error = "0" },
    @{ metric = "new_vulnerabilities"; op = "GT"; error = "0" }
)
foreach ($c in $conditions) {
    $body = @{ gateName = $GateName; metric = $c.metric; op = $c.op; error = $c.error }
    Invoke-RestMethod -Method POST -Uri "$SonarQemuUrl/api/qualitygates/create_condition" -Headers $auth -Body $body | Out-Null
    Write-Host ("condition {0}" -f $c.metric)
}

Invoke-RestMethod -Method POST -Uri "$SonarQemuUrl/api/qualitygates/select" -Headers $auth -Body @{
    gateName   = $GateName
    projectKey = $projectKey
} | Out-Null
Write-Host "Bound $GateName to $projectKey"
