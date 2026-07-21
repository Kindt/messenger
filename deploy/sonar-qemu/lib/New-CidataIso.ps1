function New-SonarCidataSeed {
    . (Join-Path $PSScriptRoot "..\config.ps1")
    New-Item -ItemType Directory -Force -Path $SonarQemuRunDir | Out-Null

    $staging = Join-Path $SonarQemuRunDir "cidata-staging"
    if (Test-Path $staging) { Remove-Item -Recurse -Force $staging }
    New-Item -ItemType Directory -Force -Path $staging | Out-Null
    Copy-Item (Join-Path $SonarQemuCloudDir "meta-data") (Join-Path $staging "meta-data") -Force
    Copy-Item (Join-Path $SonarQemuCloudDir "user-data") (Join-Path $staging "user-data") -Force

    $iso = Join-Path $SonarQemuRunDir "cidata-sonar.iso"
    $pyScript = Join-Path $SonarQemuToolsDir "mk_cidata_iso.py"

    if (-not (Get-Command python -ErrorAction SilentlyContinue)) {
        throw "python not found (required for cidata ISO)"
    }
    & python -c "import pycdlib" 2>$null
    if ($LASTEXITCODE -ne 0) {
        Write-Host "Installing pycdlib..." -ForegroundColor Cyan
        & python -m pip install pycdlib --quiet
        if ($LASTEXITCODE -ne 0) { throw "pip install pycdlib failed" }
    }
    $pyOut = & python $pyScript $staging $iso 2>&1
    if ($LASTEXITCODE -ne 0) { throw "mk_cidata_iso.py failed: $pyOut" }
    if (-not (Test-Path $iso) -or (Get-Item $iso).Length -lt 1024) {
        throw "cidata ISO missing: $iso"
    }
    Write-Host "Seed ISO: $iso" -ForegroundColor DarkGray
    # PowerShell functions emit all pipeline output — return only the path.
    return ,$iso
}
