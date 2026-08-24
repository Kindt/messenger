#Requires -Version 5.1
param([string] $ManifestPath)
$ErrorActionPreference = 'Stop'
$repo = Split-Path $PSScriptRoot -Parent
$schema = Join-Path $repo 'specs\031-desktop-java-client\contracts\update-manifest.schema.json'
if (-not (Test-Path $schema)) { Write-Error "Missing schema: $schema" }

$fixture = $ManifestPath
if (-not $fixture) {
    $fixture = Join-Path $repo 'specs\031-desktop-java-client\fixtures\update-manifest-stable.json'
}

if (-not (Test-Path $fixture)) { Write-Error "Missing manifest fixture: $fixture" }
$json = Get-Content -Raw $fixture | ConvertFrom-Json
if ($json.schema_version -ne 1) { Write-Error 'schema_version must be 1' }
if (-not $json.artifacts -or $json.artifacts.Count -lt 1) { Write-Error 'artifacts required' }

Push-Location $repo
try {
    & .\gradlew.bat :modules:desktop-client-sdk:test --tests "*UpdateServiceTest" --no-configuration-cache -q
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
}
finally {
    Pop-Location
}

Write-Host "PASS smoke-desktop-update-manifest ($fixture)"
exit 0
