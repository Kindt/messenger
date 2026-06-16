function New-KorusCidataSeed {
    param(
        [Parameter(Mandatory)][ValidateSet("server", "web", "integrations")]
        [string]$Role
    )
    . (Join-Path $PSScriptRoot "..\config.ps1")
    . (Join-Path $PSScriptRoot "New-CloudInitFat.ps1")

    $staging = New-KorusCloudInitFat -Role $Role
    $iso = Join-Path $KorusQemuRunDir "cidata-$Role.iso"
    $pyScript = Join-Path $KorusQemuDeployRoot "tools\mk_cidata_iso.py"

    if (-not (Get-Command python -ErrorAction SilentlyContinue)) {
        throw "python not found (required for cidata ISO)"
    }
    $pipCheck = & python -c "import pycdlib" 2>&1
    if ($LASTEXITCODE -ne 0) {
        Write-Host "Installing pycdlib..." -ForegroundColor Cyan
        & python -m pip install pycdlib --quiet
        if ($LASTEXITCODE -ne 0) { throw "pip install pycdlib failed" }
    }
    & python $pyScript $staging $iso
    if ($LASTEXITCODE -ne 0) { throw "mk_cidata_iso.py failed" }
    if (-not (Test-Path $iso) -or (Get-Item $iso).Length -lt 1024) {
        throw "cidata ISO missing: $iso"
    }
    Write-Host "Seed $Role : ISO (volume cidata)" -ForegroundColor DarkGray
    return @{ Type = "iso"; Path = $iso }
}
