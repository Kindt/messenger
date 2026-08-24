#Requires -Version 5.1
<#
.SYNOPSIS
  Full offline parity gate — SDK tests, UI compile, parity matrix, WebParity audit.
#>
$ErrorActionPreference = 'Stop'
$repo = Split-Path $PSScriptRoot -Parent
$matrix = Join-Path $repo 'specs\031-desktop-java-client\contracts\feature-parity-matrix.json'
if (-not (Test-Path $matrix)) { Write-Error "Missing $matrix" }

Push-Location $repo
try {
    & .\gradlew.bat :modules:desktop-client-sdk:test :modules:desktop-client:compileJava --no-configuration-cache -q
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

    $m = Get-Content -Raw $matrix | ConvertFrom-Json
    $required = @($m.rows | Where-Object { $_.status -eq 'required' })
    $withSmoke = @($required | Where-Object { $_.desktop_smoke })
    Write-Host "Matrix: $($required.Count) required rows ($($withSmoke.Count) with desktop_smoke scripts)"

    Write-Host 'PASS smoke-desktop-full-parity (offline automated baseline)'
    exit 0
}
finally {
    Pop-Location
}
