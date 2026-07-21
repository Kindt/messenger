param(
    [string]$RepoRoot,
    [string]$ProjectKey,
    [switch]$Help
)
$ErrorActionPreference = "Stop"
if ($Help) {
    Write-Host "Usage: .\deploy\sonar-qemu\fetch-issues.ps1 [-RepoRoot path] [-ProjectKey key]"
    exit 0
}
. (Join-Path $PSScriptRoot "config.ps1")
. (Join-Path $PSScriptRoot "lib\LabApi.ps1")
$auth = Get-SonarLabAuthHeaders
$key = Get-SonarLabProjectKey -RepoRoot $RepoRoot -ProjectKey $ProjectKey
$keyEnc = [uri]::EscapeDataString($key)
Write-Host "ProjectKey=$key" -ForegroundColor DarkGray

for ($i = 0; $i -lt 40; $i++) {
    $act = Invoke-RestMethod -Uri "$SonarQemuUrl/api/ce/activity?status=IN_PROGRESS,PENDING&ps=5" -Headers $auth
    if (-not $act.tasks -or $act.tasks.Count -eq 0) { break }
    Start-Sleep 3
}

$comp = Invoke-RestMethod -Uri "$SonarQemuUrl/api/measures/component?component=$keyEnc&metricKeys=bugs,vulnerabilities,code_smells,security_hotspots,sqale_index" -Headers $auth
$comp.component.measures | ForEach-Object { Write-Host ("{0}={1}" -f $_.metric, $_.value) }

$page = 1
$all = @()
do {
    $uri = "$SonarQemuUrl/api/issues/search?componentKeys=$keyEnc&resolved=false&ps=500&p=$page"
    $resp = Invoke-RestMethod -Uri $uri -Headers $auth
    $all += $resp.issues
    $total = $resp.total
    $page++
} while ($all.Count -lt $total)

Write-Host ("total={0}" -f $all.Count)
$all | Group-Object type | ForEach-Object { Write-Host ("type {0}: {1}" -f $_.Name, $_.Count) }
$all | Group-Object severity | ForEach-Object { Write-Host ("sev {0}: {1}" -f $_.Name, $_.Count) }
$all | Group-Object rule | Sort-Object Count -Descending | Select-Object -First 30 | ForEach-Object {
    Write-Host ("{0} {1}" -f $_.Count, $_.Name)
}

$rows = @(foreach ($i in $all) {
    [pscustomobject]@{
        rule     = $i.rule
        severity = $i.severity
        type     = $i.type
        message  = $i.message
        file     = ($i.component -replace '^[^:]+:', '')
        line     = $i.line
        key      = $i.key
    }
})
$out = Join-Path $SonarQemuRunDir "issues.json"
New-Item -ItemType Directory -Force -Path $SonarQemuRunDir | Out-Null
$json = if ($rows.Count -eq 0) { "[]" } else { $rows | ConvertTo-Json -Depth 4 }
Set-Content -Encoding utf8 -Path $out -Value $json
Write-Host "saved $out"
